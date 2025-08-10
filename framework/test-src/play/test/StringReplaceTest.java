package play.test;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;

public class StringReplaceTest {

    public static void main(String[] args) {
       /* String test = "aku adalah \n anak";
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        for(int i=0;i<10000;i++) {
            String name = StringUtils.replace(test, "\n", "<br/>");
//            System.out.println(name);
        }
        stopWatch.stop();
        System.out.println("StringUtils.replace time : "+stopWatch.getTime());
        stopWatch.reset();
        stopWatch.start();
        for(int i=0;i<10000;i++) {
            String name = test.replace("\n", "<br/>");
//            System.out.println(name);
        }
        stopWatch.stop();
        System.out.println("String.replace time : "+stopWatch.getTime());*/
        String content = "<!DOCTYPE html>\n\n<html>\n<head><title>Home</title>\n<link rel=\"stylesheet\" type=\"text/css\" media=\"screen\" href=\"/public/stylesheets/main.css\">\n </head>\n<body>\n\n\n\ndata\n</body>\n</html>\n";
        System.out.println(StringUtils.replace(content, "\n", ""));
    }
}