package local.generalsmc.client;

/** Prepare Windows stack pages while still in Java, before shaderc calls back from a deep native stack. */
public final class ShaderCallbackStack {
    private static final ThreadLocal<Boolean> prepared=ThreadLocal.withInitial(()->false);
    private static final java.util.concurrent.atomic.AtomicInteger threads=new java.util.concurrent.atomic.AtomicInteger();
    private static volatile long sink;
    private ShaderCallbackStack() {}
    public static int preparedThreads(){return threads.get();}
    public static void prepare(){
        if(!System.getProperty("os.name","").startsWith("Windows")||!Boolean.parseBoolean(System.getProperty("generalsmc.shaderStackGuard","true"))||prepared.get())return;
        // Windows grows a thread stack through guard pages. A compiled Java method's
        // 32 KiB shadow-stack probe can skip that guard after a deep native GLSL call.
        // Touch the pages with ordinary Java frames before entering shaderc. Keeping
        // arguments live also retains stack frames after this method gets compiled.
        try{sink=reserve(1024,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16);}
        catch(StackOverflowError smallStack){/* The usable pages of a smaller custom stack are now committed. */}
        prepared.set(true);threads.incrementAndGet();
    }
    private static long reserve(int depth,long a,long b,long c,long d,long e,long f,long g,long h,long i,long j,long k,long l,long m,long n,long o,long p){
        long value=a^b^c^d^e^f^g^h^i^j^k^l^m^n^o^p;
        if(depth==0)return value;
        return reserve(depth-1,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,value+depth)+value;
    }
}
