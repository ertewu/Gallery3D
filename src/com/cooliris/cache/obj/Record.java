package com.cooliris.cache.obj;

class Record {
    public Record(int chunk, int offset, int size, int sizeOnDisk,
            long timestamp) {
        this.chunk = chunk;
        this.offset = offset;
        this.size = size;
        this.timestamp = timestamp;
        this.sizeOnDisk = sizeOnDisk;
    }

    public final long timestamp;
    public final int chunk;
    public final int offset;
    public final int size;
    public final int sizeOnDisk;
}
