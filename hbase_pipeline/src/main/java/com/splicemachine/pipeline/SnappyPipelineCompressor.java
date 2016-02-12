package com.splicemachine.pipeline;

import com.splicemachine.access.HConfiguration;
import com.splicemachine.pipeline.utils.PipelineCompressor;
import com.splicemachine.utils.SpliceLogUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.compress.SnappyCodec;
import org.apache.log4j.Logger;

import java.io.*;
import java.lang.reflect.Method;

/**
 * @author Scott Fines
 *         Date: 12/29/15
 */
public class SnappyPipelineCompressor implements PipelineCompressor{
    private static final Logger LOG=Logger.getLogger(SnappyPipelineCompressor.class);
    private static final SnappyCodec snappy;
    private static final boolean supportsNative;

    static{
        snappy = new SnappyCodec();
        snappy.setConf(HConfiguration.INSTANCE.unwrapDelegate());
        boolean sN;
        Method method;
        try{
            // Cloudera Path
            method=SnappyCodec.class.getMethod("isNativeCodeLoaded",null);
            sN=(Boolean)method.invoke(snappy,null);
        }catch(Exception e){
            SpliceLogUtils.error(LOG,"basic snappy codec not supported, checking alternative method signature");
            try{
                method=SnappyCodec.class.getMethod("isNativeSnappyLoaded",Configuration.class);
                sN=(Boolean)method.invoke(snappy,HConfiguration.INSTANCE.unwrapDelegate());
            }catch(Exception ioe){
                SpliceLogUtils.error(LOG,"Alternative signature did not work, No Snappy Codec Support",ioe);
                sN=false;
            }
        }
        if(!sN)
            SpliceLogUtils.error(LOG,"No Native Snappy Installed: Splice Machine's Write Pipeline will not compress data over the wire.");
        else
            SpliceLogUtils.info(LOG,"Snappy Installed: Splice Machine's Write Pipeline will compress data over the wire.");
        supportsNative = sN;
    }

    private final PipelineCompressor delegate;

    public SnappyPipelineCompressor(PipelineCompressor delegate){
        this.delegate=delegate;
    }

    @Override
    public InputStream compressedInput(InputStream input) throws IOException{
        if(supportsNative)
            return snappy.createInputStream(input);
        else return input;
    }

    @Override
    public OutputStream compress(OutputStream output) throws IOException{
        if(supportsNative)
            return snappy.createOutputStream(output);
        else return output;
    }

    @Override
    public byte[] compress(Object o) throws IOException{
        byte[] d = delegate.compress(o);
        if(!supportsNative) return d;
        ByteArrayOutputStream baos = new ByteArrayOutputStream(d.length);
        OutputStream os = snappy.createOutputStream(baos);
        os.write(d);
        os.flush();
        os.close();
        return baos.toByteArray();
    }

    @Override
    public <T> T decompress(byte[] bytes,Class<T> clazz) throws IOException{
        throw new UnsupportedOperationException("IMPLEMENT");
    }
}