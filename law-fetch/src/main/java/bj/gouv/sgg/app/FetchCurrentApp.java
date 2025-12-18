package bj.gouv.sgg.app;

import bj.gouv.sgg.job.fetch.FetchJob;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FetchCurrentApp {

    public static void main(String[] args) {
        System.out.println("Lancement du Fetch Current App...");
        FetchJob fetchJob = new FetchJob();

//        fetchJob.runDocument("loi-2025-18");
//        fetchJob.runCurrent("loi");
        fetchJob.runPrevious("decret", 10);
        fetchJob.runCurrent("loi");
        System.out.println("Fetch Current App terminé.");

    }

}
