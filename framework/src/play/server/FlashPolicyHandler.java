package play.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.CharsetUtil;

import java.util.List;

public class FlashPolicyHandler extends ByteToMessageDecoder {

    private static final String XML = "<cross-domain-policy><allow-access-from domain=\"*\" to-ports=\"*\" /></cross-domain-policy>";
    private final ByteBuf policyResponse = Unpooled.copiedBuffer(XML, CharsetUtil.UTF_8);

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> out) {
        if (byteBuf.readableBytes() < 2) {
            return;
        }

        int magic1 = byteBuf.getUnsignedByte(byteBuf.readerIndex());
        int magic2 = byteBuf.getUnsignedByte(byteBuf.readerIndex() + 1);
        boolean isFlashPolicyRequest = (magic1 == '<' && magic2 == 'p');

        if (isFlashPolicyRequest) {
            byteBuf.skipBytes(byteBuf.readableBytes()); // Discard everything
            ctx.writeAndFlush(policyResponse).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        // Remove ourselves, important since the byte length check at top can hinder frame decoding
        // down the pipeline
        ctx.pipeline().remove(this);
        out.add(byteBuf.readBytes(byteBuf.readableBytes()));
    }

}
