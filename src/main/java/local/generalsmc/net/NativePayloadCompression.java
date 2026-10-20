package local.generalsmc.net;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.zip.*;

/** Bounded binary payload compression, also effective on integrated/LAN connections. */
public final class NativePayloadCompression {
    private NativePayloadCompression(){}
    public static byte[] encode(byte[] raw){
        if(raw.length>=512){
            var compressor=new Deflater(Deflater.BEST_SPEED,true);
            try{
                compressor.setInput(raw);compressor.finish();var out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];
                while(!compressor.finished()){int count=compressor.deflate(buffer);out.write(buffer,0,count);}
                if(out.size()+5<raw.length-24){var result=ByteBuffer.allocate(out.size()+5);result.put((byte)1).putInt(raw.length).put(out.toByteArray());return result.array();}
            }finally{compressor.end();}
        }
        byte[] result=new byte[raw.length+1];System.arraycopy(raw,0,result,1,raw.length);return result;
    }
    public static byte[] decode(byte[] packed,int limit)throws IOException{
        if(packed.length<1||packed.length>limit+5)throw new IOException("Invalid compressed payload size");
        if(packed[0]==0){if(packed.length-1>limit)throw new IOException("Payload exceeds limit");return java.util.Arrays.copyOfRange(packed,1,packed.length);}
        if(packed[0]!=1||packed.length<5)throw new IOException("Invalid compressed payload header");
        int length=ByteBuffer.wrap(packed,1,4).getInt();if(length<0||length>limit)throw new IOException("Expanded payload exceeds limit");
        var inflater=new Inflater(true);
        try{
            inflater.setInput(packed,5,packed.length-5);byte[] result=new byte[length];int at=0;
            while(at<length){int count=inflater.inflate(result,at,length-at);if(count==0)throw new IOException("Truncated compressed payload");at+=count;}
            if(!inflater.finished()||inflater.getRemaining()!=0)throw new IOException("Invalid compressed payload boundary");return result;
        }catch(DataFormatException broken){throw new IOException("Invalid compressed payload",broken);}finally{inflater.end();}
    }
}
