package com.vidkeep.common;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 分页响应：{ "total": 100, "page": 1, "size": 20, "records": [] }。
 */
public class PageResult<T> {

    private long total;
    private long page;
    private long size;
    private List<T> records;

    public PageResult() {
    }

    public PageResult(long total, long page, long size, List<T> records) {
        this.total = total;
        this.page = page;
        this.size = size;
        this.records = records;
    }

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }
}
