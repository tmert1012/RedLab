package com.isidorefarm.redlab.entities;

public class AttachmentStat implements Comparable<AttachmentStat> {

    private String filename;
    private Integer count;

    public AttachmentStat(String filename) {
        this.filename = filename;
        count = 0;
    }

    public String getFilename() {
        return filename;
    }

    public Integer getCount() {
        return count;
    }

    public void increment() {
        count++;
    }

    @Override
    public int compareTo(AttachmentStat attachmentStat) {
        return attachmentStat.getCount().compareTo(count);
    }
}
