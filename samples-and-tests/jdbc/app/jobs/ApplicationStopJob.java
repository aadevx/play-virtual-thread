package jobs;

import play.Logger;
import play.jobs.Job;
import play.jobs.OnApplicationStop;

@OnApplicationStop
public class ApplicationStopJob extends Job {

	@Override
	public void doJob() throws Exception {
		Logger.info("stopping....job");
	}
}
