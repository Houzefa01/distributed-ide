package com.distributed_ide.javafx_client.model;

public class JavaLaunchConfig {
    private String mainClass;
    private String vmArgs;
    private String programArgs;
    private String classPathExtra;

    public String getMainClass() {
        return mainClass;
    }

    public void setMainClass(String mainClass) {
        this.mainClass = mainClass;
    }

    public String getVmArgs() {
        return vmArgs;
    }

    public void setVmArgs(String vmArgs) {
        this.vmArgs = vmArgs;
    }

    public String getProgramArgs() {
        return programArgs;
    }

    public void setProgramArgs(String programArgs) {
        this.programArgs = programArgs;
    }

    public String getClassPathExtra() {
        return classPathExtra;
    }

    public void setClassPathExtra(String classPathExtra) {
        this.classPathExtra = classPathExtra;
    }
}
