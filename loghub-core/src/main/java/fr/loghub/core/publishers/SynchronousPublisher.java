package fr.loghub.core.publishers;


import org.apache.logging.log4j.Logger;

import lombok.Getter;

abstract class SynchronousPublisher implements Publisher {

    @Getter
    private volatile boolean closed;
    private final Logger logger;

    protected SynchronousPublisher(Logger logger) {
        this.logger = logger;
    }

    abstract void refreshSocket() throws InterruptedException;

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
    }

}
