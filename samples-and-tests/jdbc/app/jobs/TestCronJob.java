package jobs;

import play.Logger;
import play.jobs.Job;
import play.jobs.On;

import java.util.Date;

//@On("*/1 * * * * ?")
public class TestCronJob extends Job {

    @Override
    public void doJob() throws Exception {
        Logger.info("TestCronJob....started : %s", new Date());
    }
}
