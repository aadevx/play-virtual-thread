package play.plugins;


import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.mvc.Http;
import play.mvc.Http.Request;
import play.mvc.Scope.Session;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

public class SlowActionMonitor extends PlayPlugin {

	private static final ThreadLocal<Long> stopWatch=new ThreadLocal<>();
	private static int threshold=Integer.MAX_VALUE;
	
	@Override
	public void beforeActionInvocation(Method actionMethod) {
		stopWatch.set(System.currentTimeMillis());
	}

	@Override
	public void onInvocationException(Throwable e) {
		stopWatch.remove();
	}

	@Override
	public void invocationFinally() {
		/**
		  Jika ada error apapun, abaikan supaya tidak mengganggu action yg sedang berlangsung
		 **/
			Long sw=stopWatch.get();
			if(sw != null) {
				stopWatch.remove();
				Long duration = System.currentTimeMillis() - sw;
				Request request = Request.current();
				if (duration > threshold) {
					Session sess = Session.current();
					if (sess == null || sess.isEmpty())
						Logger.warn("[SLOW] %s [%s] duration: %s", request.url, request.action, duration);
					else
						Logger.warn("[SLOW] %s duration: %s [%s] \n session: %s", request.url, request.action, duration, sess.all().toString());
				}
			}
	}

	@Override
	public void onApplicationReady() {
		String key="slow.action.monitor.threshold";
		String val=Play.configuration.getProperty(key, "5");
		Logger.info("[application.conf] %s=%s (in seconds)", key, val);
		threshold=Integer.valueOf(val) * 1000;
	}

	@Override
	public boolean rawInvocation(Request request, Http.Response response) throws Exception {
		String httpPath = Play.configuration.getProperty("http.path", "");
		if (request.path.startsWith(httpPath+"/@error/")) {
			if (!Play.started) {
				response.print("Application is not started");
				response.status = 503;
				return true;
			}
			response.contentType = "text/plain";
			Http.Header authorization = request.headers.get("authorization");
			String statusKey = Play.configuration.getProperty("application.statusKey", System.getProperty("statusKey", Play.secretKey));
			if (authorization != null && statusKey != null && statusKey.equals(authorization.value())) {
				String id = request.path.replace(httpPath+"/@error/", "");
				String logMessage = findLogMessage(id);
				if(StringUtils.isEmpty(logMessage))
					logMessage="NotFound";
				response.print(logMessage);
				response.status = 200;
				return true;
			}
			response.status = 401;
			response.print("Not authorized");
			return true;
		}
		return super.rawInvocation(request, response);
	}

	private String findLogMessage(String id)
	{
		long start = System.currentTimeMillis();
		LoggerContext context= (LoggerContext) LogManager.getContext();
		Configuration config= context.getConfiguration();
		RollingFileAppender appender = config.getAppender("RollingFile");
		if(appender == null)
			return null;
		File fileLogger = new File(appender.getFileName());
//		Logger.info("log file : %s", fileLogger);
		if(!fileLogger.exists())
			return null;
		//urutkan berdasarkan last modif
		Set<File> files=new TreeSet<>((o1, o2) -> {
			if (o1.lastModified() < o2.lastModified())
				return 1;
			return -1;
		});
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(fileLogger.getParentFile().toPath())){
			for (Path path : stream) {
				files.add(path.toFile());
			}
		} catch (IOException e) {
			Logger.error(e,e.getMessage());
		}
		if(!id.startsWith("@"))
			id="@" + id;
		int fileScan=1;
		String msg=null;
		long fileSize=0;
		for(File file: files)
		{
			msg=findInFile(file, id);
			Logger.debug("Searching in file: %s of %s", file,files.size());
			fileSize+=file.length();
			if(!msg.isEmpty())
				break;
			fileScan++;
		}
		Logger.debug("Find text %s in log files, files scanned: %s (%s), duration: %s", id, fileScan,
				FileUtils.byteCountToDisplaySize(fileSize), System.currentTimeMillis() - start );
		if(msg.isEmpty())
			msg=null;
		return msg;
	}

	private String findInFile(File file, String id) {
		CircularFifoQueue<String> previousString=new CircularFifoQueue<>(10); //simpan beberapa baris sebelumnya
		try (LineNumberReader reader=new LineNumberReader(new BufferedReader(new FileReader(file), 1024*100))){
			String st=null;
			boolean startIsFound=false;
			StringBuilder strResult=new StringBuilder(1024*10);
			while((st=reader.readLine())!=null)
			{
				if(startIsFound)
				{
					strResult.append(st).append('\n');
					if(st.startsWith("\t... "))
					{
						if((st=reader.readLine())!=null && st.startsWith("Caused by"))
							strResult.append(st).append('\n');
						else {
							//tambahkan beberapa baris sebelumnya ke dalam result
							for (String stPrev : previousString)
								strResult.insert(0, stPrev).insert(0, '\n');
							break;
						}
					}
				}
				else
					previousString.add(st);
				if(st.startsWith(id))
				{
					strResult.append("\n===================================================================================================\n").append(st).append('\n');
					startIsFound=true;
				}
			}

			reader.close();
			return strResult.toString();


		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

}
