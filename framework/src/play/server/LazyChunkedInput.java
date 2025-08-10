package play.server;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.stream.ChunkedInput;

import java.util.concurrent.ConcurrentLinkedQueue;

import static io.netty.buffer.Unpooled.wrappedBuffer;

public class LazyChunkedInput implements ChunkedInput {

    private boolean closed = false;
    private final ConcurrentLinkedQueue<byte[]> nextChunks = new ConcurrentLinkedQueue<>();
    private long offset;

    @Override
    public Object readChunk(ChannelHandlerContext ctx) throws Exception {
        return readChunk(ctx.alloc());
    }


    @Override
    public Object readChunk(ByteBufAllocator byteBufAllocator) throws Exception {
        if (this.isEndOfInput()) {
            return null;
        } else {
            if (hasNextChunk())
                return nextChunk();
            return null;
        }
    }

    @Override
    public long length() {
      return nextChunks.size();
//        return -1L;
    }

    @Override
    public long progress() {
        return offset;
    }

    public boolean hasNextChunk() throws Exception {
        return !nextChunks.isEmpty();
    }

    public Object nextChunk() throws Exception {
        if (nextChunks.isEmpty()) {
            return null;
        }
        byte[] next = nextChunks.poll();
        offset = next.length;
        return wrappedBuffer(next);
    }

    @Override
    public boolean isEndOfInput() throws Exception {
        return closed && nextChunks.isEmpty();
    }

    @Override
    public void close() throws Exception {
        closed = true;
    }

    public void writeChunk(Object chunk, String encoding) throws Exception {
        if (closed) {
            throw new Exception("HTTP output stream closed");
        }

        byte[] bytes;
        if (chunk instanceof byte[] bytesVal) {
            bytes = bytesVal;
        } else {
            String message = chunk == null ? "" : chunk.toString();
            bytes = message.getBytes(encoding);
        }

        nextChunks.offer(bytes);
    }
}
