package dev.patchreceipt.web;

public final class QueueFullException extends RuntimeException {

    public QueueFullException() {
        super("The verifier queue is full. Try again after the active run completes.");
    }
}
