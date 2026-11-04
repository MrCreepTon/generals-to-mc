package local.generalsmc.nativebridge;

import java.io.IOException;
import java.util.ArrayDeque;

/** Ordered disk writes without blocking a render or server tick. */
final class NativeFileQueue {
    @FunctionalInterface interface Write { void run() throws IOException; }
    private record Entry(int bytes, Write write) {}
    private final ArrayDeque<Entry> pending = new ArrayDeque<>();
    private final Thread worker;
    private int bytes;
    private boolean closed;
    private IOException failure;

    NativeFileQueue() {
        worker = Thread.ofVirtual().name("zhmc-transport-writer").start(this::run);
    }

    synchronized void check() throws IOException {
        if (failure != null) throw new IOException("Native transport write failed", failure);
    }

    synchronized void submit(int size, Write write) throws IOException {
        check();
        if (closed) throw new IOException("Native transport is closed");
        if (pending.size() >= 2048 || (long) bytes + size > 8 * 1024 * 1024)
            throw new IOException("Native transport write queue is full");
        pending.add(new Entry(size, write));
        bytes += size;
        notifyAll();
    }

    synchronized void finish() { closed = true; notifyAll(); }
    void await(long millis) throws InterruptedException { worker.join(millis); }

    private void run() {
        try {
            while (true) {
                Entry entry;
                synchronized (this) {
                    while (pending.isEmpty() && !closed) wait();
                    if (pending.isEmpty()) return;
                    entry = pending.remove();
                }
                entry.write.run();
                synchronized (this) { bytes -= entry.bytes; }
            }
        } catch (IOException | RuntimeException e) {
            synchronized (this) {
                failure = e instanceof IOException io ? io : new IOException(e);
                pending.clear(); bytes = 0; closed = true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            synchronized (this) { failure = new IOException("Native transport writer interrupted", e); }
        }
    }
}
