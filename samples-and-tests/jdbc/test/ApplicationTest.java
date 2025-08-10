import models.Sub_tag;
import org.junit.Before;
import org.junit.Test;
import play.Logger;
import play.mvc.Http.Response;
import play.test.FunctionalTest;

public class ApplicationTest extends FunctionalTest {

    @Before
    public void setup() {
        Logger.info("setup...");
    }
    @Test
    public void testThatIndexPageWorks() {
        Sub_tag sub_tag = Sub_tag.find("stg_id='BAP'").first();
        Response response = POST("/application/file");
//        assertIsOk(response);
//        assertContentType("text/html", response);
//        assertCharset(play.Play.defaultWebEncoding, response);
    }
    
}