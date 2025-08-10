package play.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import play.Play;
import play.libs.IO;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

public class StreamChunkAggregator extends SimpleChannelInboundHandler<Object> {

    private volatile HttpMessage currentMessage;
    private volatile OutputStream out;
    private static final int maxContentLength = Integer.valueOf(Play.configuration.getProperty("play.netty.maxContentLength", "-1"));
    private volatile File file;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof HttpMessage) && !(msg instanceof HttpChunkedInput)) {
            ctx.fireChannelRead(msg);
            return;
        }

        HttpMessage currentMessage = this.currentMessage;
        File localFile = this.file;
        if (currentMessage == null) {
            HttpMessage m = (HttpMessage) msg;
            if (HttpUtil.isTransferEncodingChunked(m)) {
                String localName = UUID.randomUUID().toString();
                // A chunked message - remove 'Transfer-Encoding' header,
                // initialize the cumulative buffer, and wait for incoming chunks.
                List<String> encodings = m.headers().getAll(HttpHeaderNames.TRANSFER_ENCODING);
                encodings.remove(HttpHeaderValues.CHUNKED.toString());
                if (encodings.isEmpty()) {
                    m.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
                }
                this.currentMessage = m;
                this.file = new File(Play.tmpDir, localName);
                this.out = new FileOutputStream(file, true);
            } else {
                // Not a chunked message - pass through.
                ctx.fireChannelRead(msg);
            }
        } else {
            // TODO: If less that threshold then in memory
            // Merge the received chunk into the content of the current message.
            HttpContent httpContent = (HttpContent) msg;
            ByteBuf content = httpContent.content();
            if (maxContentLength != -1 && (localFile.length() > (maxContentLength - content.readableBytes()))) {
                currentMessage.headers().set(HttpHeaderNames.WARNING, "play.netty.content.length.exceeded");
            } else {
                IO.copy(new ByteBufInputStream(content), this.out);
                if (msg instanceof LastHttpContent) {

                    this.out.flush();
                    this.out.close();
                    currentMessage.headers().set(HttpHeaderNames.CONTENT_LENGTH,String.valueOf(localFile.length()));
//                    currentMessage.setContent(new FileChannelBuffer(localFile));
                    this.out = null;
                    this.currentMessage = null;
                    this.file.delete();
                    this.file = null;
                    ctx.fireChannelRead(currentMessage);
                }
            }
        }

    }
}

