import org.asynchttpclient.*;
import org.asynchttpclient.request.body.multipart.FilePart;
import org.junit.*;

import java.io.File;
import java.util.*;

import play.Logger;
import play.Play;
import play.libs.WS;
import play.test.*;
import models.*;

public class BasicTest extends UnitTest {

    @Test
    public void aVeryImportantThingToTest() {
        assertEquals(2, 1 + 1);
    }

    public static void main(String[] args) {
        try  {
            File file = Play.getFile("public/images/favicon.png");
            if (!file.exists()) {
                Logger.info("file null or not exist");
                return;
            } else {
                Logger.info("file :%s", file);
                Logger.info("Ukuran :%s", file.length());
            }
//            WS.HttpResponse response = WS.url("http://localhost:9000/application/file").files(file).postAsync().get();
            Request builder = new RequestBuilder()
                    .setMethod("POST")
                    .setUrl("http://localhost:9000/application/file")
                    .addBodyPart(new FilePart("testFile", file))
                    .build();
            Logger.info("Builder %s", builder);
            AsyncHttpClientConfig config = Dsl.config().setConnectTimeout(35000).setReadTimeout(35000).build();
            AsyncHttpClient asyncHttpClient = Dsl.asyncHttpClient(config);
            Response response = asyncHttpClient.prepareRequest(builder).execute().get();
            if (response != null) {
                Logger.info("response : %s", response.getResponseBody());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
