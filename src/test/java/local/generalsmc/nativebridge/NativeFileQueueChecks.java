package local.generalsmc.nativebridge;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class NativeFileQueueChecks {
    public static int run() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var values=new ArrayList<Integer>();var queue=new NativeFileQueue();
        queue.submit(1,()->{entered.countDown();try{release.await();}catch(InterruptedException e){throw new IOException(e);}});
        if(!entered.await(2,TimeUnit.SECONDS))throw new AssertionError("Writer did not start");
        try {
            for(int i=0;i<100;i++){int value=i;queue.submit(1,()->values.add(value));}
            if(!values.isEmpty())throw new AssertionError("Blocked writes ran on the submitting thread");
            queue.finish();
            try{queue.submit(1,()->{});throw new AssertionError("Closed queue accepted a write");}catch(IOException expected){}
        } finally { release.countDown();queue.finish();queue.await(2000); }
        if(values.size()!=100)throw new AssertionError("Close lost pending writes");
        for(int i=0;i<100;i++)if(values.get(i)!=i)throw new AssertionError("Write order changed");
        var failed=new NativeFileQueue();failed.submit(1,()->{throw new IOException("fixture failure");});failed.finish();failed.await(2000);
        try{failed.check();throw new AssertionError("Write failure was hidden");}catch(IOException expected){if(expected.getCause()==null)throw expected;}
        var limited=new NativeFileQueue();
        try{limited.submit(8*1024*1024+1,()->{});throw new AssertionError("Unbounded write accepted");}catch(IOException expected){}finally{limited.finish();limited.await(2000);}
        return 5;
    }
}
