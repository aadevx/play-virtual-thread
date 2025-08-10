package play.server;


import io.netty.buffer.AbstractByteBuf;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import play.Logger;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.file.Files;

import static io.netty.buffer.Unpooled.wrappedBuffer;

/**
 * Useless channel buffer only used to wrap the input stream....
 */
public final class FileChannelBuffer extends AbstractByteBuf
{
    private static final String NOT_SUPPORTED = "Not supported";
    private static final String READ_ONLY = "Read Only.";
    private final File file;
    private final long length;
    private int refCnt = 1;

    public FileChannelBuffer( File file )
            throws FileNotFoundException
    {
        super( Integer.MAX_VALUE );
        this.file = file;
        this.length = file.length();
    }

    @Override
    public  int readableBytes()
    {
        if( length < Integer.MIN_VALUE || length > Integer.MAX_VALUE )
        {
            throw new IllegalArgumentException( length + " cannot be cast to int without changing its value." );
        }
        return (int) length;
    }

    public  InputStream getInputStream()
    {
        try
        {
            return new FileInputStream( file );
        }
        catch( FileNotFoundException e )
        {
            throw new RuntimeException(e);
        }
    }

    public  byte[] readAllBytes()
    {
        try
        {
            return Files.readAllBytes( file.toPath() );
        }
        catch( IOException e )
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected  byte _getByte( int index )
    {
        try( RandomAccessFile raf = new RandomAccessFile( file, "r" ) )
        {
            raf.seek( index );
            return raf.readByte();
        }
        catch( IOException e )
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected  short _getShort( int index )
    {
        try( RandomAccessFile raf = new RandomAccessFile( file, "r" ) )
        {
            raf.seek( index );
            return raf.readShort();
        }
        catch( IOException e )
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected short _getShortLE(int i) {
        return 0;
    }

    @Override
    protected  int _getUnsignedMedium( int index )
    {
        throw new UnsupportedOperationException( NOT_SUPPORTED );
    }

    @Override
    protected int _getUnsignedMediumLE(int i) {
        return 0;
    }

    @Override
    protected  int _getInt( int index )
    {
        try( RandomAccessFile raf = new RandomAccessFile( file, "r" ) )
        {
            raf.seek( index );
            return raf.readInt();
        }
        catch( IOException e )
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected int _getIntLE(int i) {
        return 0;
    }

    @Override
    protected  long _getLong( int index )
    {
        try( RandomAccessFile raf = new RandomAccessFile( file, "r" ) )
        {
            raf.seek( index );
            return raf.readLong();
        }
        catch( IOException e )
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected long _getLongLE(int i) {
        return 0;
    }

    @Override
    protected  void _setByte( int index, int value )
    {
        throw new UnsupportedOperationException( READ_ONLY );
    }

    @Override
    protected  void _setShort( int index, int value )
    {
        throw new UnsupportedOperationException( READ_ONLY );
    }

    @Override
    protected void _setShortLE(int i, int i1) {

    }

    @Override
    protected  void _setMedium( int index, int value )
    {
        throw new UnsupportedOperationException( READ_ONLY );
    }

    @Override
    protected void _setMediumLE(int i, int i1) {

    }

    @Override
    protected  void _setInt( int index, int value )
    {
        throw new UnsupportedOperationException( READ_ONLY );
    }

    @Override
    protected void _setIntLE(int i, int i1) {

    }

    @Override
    protected  void _setLong( int index, long value )
    {
        throw new UnsupportedOperationException( READ_ONLY );
    }

    @Override
    protected void _setLongLE(int i, long l) {

    }

    @Override
    public  int capacity()
    {
        if( length < Integer.MIN_VALUE || length > Integer.MAX_VALUE )
        {
            throw new IllegalArgumentException( length + " cannot be cast to int without changing its value." );
        }
        return (int) length;
    }

    @Override
    public  ByteBuf capacity( int newCapacity )
    {
        throw new UnsupportedOperationException( READ_ONLY );
    }

    @Override
    public ByteBufAllocator alloc()
    {
        return null;
    }

    @Override
    public  ByteOrder order()
    {
        return ByteOrder.nativeOrder();
    }

    @Override
    public  ByteBuf unwrap()
    {
        return null;
    }

    @Override
    public  boolean isDirect()
    {
        return true;
    }

    @Override
    public  ByteBuf getBytes( int index, ByteBuf dst, int dstIndex, int length )
    {
        throw new UnsupportedOperationException( NOT_SUPPORTED );
    }

    @Override
    public  ByteBuf getBytes( int index, byte[] dst, int dstIndex, int length )
    {
        checkIndex( index, length );
        checkDstIndex( index, length, dstIndex, dst.length );
        try( RandomAccessFile raf = new RandomAccessFile( file, "r" ) )
        {
            raf.seek( index );
            raf.read( dst, dstIndex, length );
            return this;
        }
        catch( IOException e )
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public  ByteBuf getBytes( int index, ByteBuffer dst )
    {
        checkIndex( index );
        try( RandomAccessFile raf = new RandomAccessFile( file, "r" ) )
        {
            raf.seek( index );
            byte[] buffer = new byte[ 8 ];
            while( dst.position() < dst.limit() )
            {
                int read = raf.read( buffer );
                if( read == -1 )
                {
                    break;
                }
                dst.put( buffer, 0, read );
            }
            return this;
        }
        catch( IOException e )
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public  ByteBuf getBytes( int index, OutputStream out, int length )
            throws IOException
    {
        throw new UnsupportedOperationException( NOT_SUPPORTED );
    }

    @Override
    public  int getBytes( int index, GatheringByteChannel out, int length )
            throws IOException
    {
        throw new UnsupportedOperationException( NOT_SUPPORTED );
    }

    @Override
    public int getBytes(int i, FileChannel fileChannel, long l, int i1) throws IOException {
        return 0;
    }

    @Override
    public  ByteBuf setBytes( int index, ByteBuf src, int srcIndex, int length )
    {
        throw new UnsupportedOperationException( READ_ONLY );
    }

    @Override
    public  ByteBuf setBytes( int index, byte[] src, int srcIndex, int length )
    {
        throw new UnsupportedOperationException( READ_ONLY );
    }

    @Override
    public  ByteBuf setBytes( int index, ByteBuffer src )
    {
        throw new UnsupportedOperationException( READ_ONLY );
    }

    @Override
    public  int setBytes( int index, InputStream in, int length )
            throws IOException
    {
        throw new UnsupportedOperationException( READ_ONLY );
    }

    @Override
    public  int setBytes( int index, ScatteringByteChannel in, int length )
            throws IOException
    {
        throw new UnsupportedOperationException( READ_ONLY );
    }

    @Override
    public int setBytes(int i, FileChannel fileChannel, long l, int i1) throws IOException {
        return 0;
    }

    @Override
    public  ByteBuf copy( int index, int length )
    {
        byte[] buf = new byte[ length ];
        try( RandomAccessFile raf = new RandomAccessFile( file, "r" ) )
        {
            raf.read( buf, index, length );
            return wrappedBuffer( buf );
        }
        catch( IOException e )
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public  int nioBufferCount()
    {
        return -1;
    }

    @Override
    public  ByteBuffer nioBuffer( int index, int length )
    {
        throw new UnsupportedOperationException( NOT_SUPPORTED );
    }

    @Override
    public  ByteBuffer[] nioBuffers( int index, int length )
    {
        throw new UnsupportedOperationException( NOT_SUPPORTED );
    }

    @Override
    public  boolean hasArray()
    {
        return false;
    }

    @Override
    public  byte[] array()
    {
        throw new UnsupportedOperationException( NOT_SUPPORTED );
    }

    @Override
    public  int arrayOffset()
    {
        throw new UnsupportedOperationException( NOT_SUPPORTED );
    }

    @Override
    public  boolean hasMemoryAddress()
    {
        return false;
    }

    @Override
    public  long memoryAddress()
    {
        throw new UnsupportedOperationException( NOT_SUPPORTED );
    }

    @Override
    public  ByteBuf retain( int increment )
    {
        refCnt += increment;
        return this;
    }

    @Override
    public  ByteBuf retain()
    {
        return retain( 1 );
    }

    @Override
    public ByteBuf touch() {
        return null;
    }

    @Override
    public ByteBuf touch(Object o) {
        return null;
    }

    @Override
    public  int refCnt()
    {
        return refCnt;
    }

    @Override
    public  boolean release()
    {
        return release( 1 );
    }

    @Override
    public  boolean release( int decrement )
    {
        refCnt -= decrement;
        if( refCnt <= 0 )
        {
            try
            {
                Files.deleteIfExists( file.toPath() );
            }
            catch( IOException ex )
            {
                Logger.warn( "Unable to delete File in FileByteBuff on release: {}", ex.getMessage(), ex );
            }
            return true;
        }
        return false;
    }

    @Override
    public  ByteBuffer internalNioBuffer( int index, int length )
    {
        throw new UnsupportedOperationException( NOT_SUPPORTED );
    }
}