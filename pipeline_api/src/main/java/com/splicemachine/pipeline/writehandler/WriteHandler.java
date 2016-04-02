package com.splicemachine.pipeline.writehandler;

import com.splicemachine.kvpair.KVPair;
import com.splicemachine.pipeline.context.WriteContext;

import java.io.IOException;
import java.util.List;

/**
 * Simple Interface for Handling Writes for a giving write pipeline (WriteContext).
 * 
 * @author Scott Fines
 * Created on: 4/30/13
 */
public interface WriteHandler {

	/**
	 * Process the mutation with the given handler
	 */
    void next(KVPair mutation, WriteContext ctx);

    /**
     * Flush the writes with the given handler.  This method assumes possible asynchronous underlying calls.
     */
    void flush(WriteContext ctx) throws IOException;

    /**
     * This closes the writes with the given handler.  It will need to wait for all underlying calls to finish or
     * throw exceptions.
     */
    void close(WriteContext ctx) throws IOException;

}