package com.distributed_ide.project_manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

public final class ProjectManagerApp {
    private static final Logger LOG = LoggerFactory.getLogger(ProjectManagerApp.class);

    public static void main(String[] args) throws Exception {
        LOG.info("ProjectManagerApp started (no network server implemented yet)");
        new CountDownLatch(1).await();
    }
}
