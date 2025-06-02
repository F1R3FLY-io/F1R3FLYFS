package io.f1r3fly.f1r3drive.fuse;

import com.kenai.jffi.MemoryIO;
import io.f1r3fly.f1r3drive.app.F1r3flyFuse;
import io.f1r3fly.f1r3drive.fuse.flags.FuseBufFlags;
import io.f1r3fly.f1r3drive.fuse.struct.FileStat;
import io.f1r3fly.f1r3drive.fuse.struct.FuseFileInfo;
import io.f1r3fly.f1r3drive.fuse.struct.FusePollhandle;
import io.f1r3fly.f1r3drive.fuse.struct.Timespec;
import jnr.ffi.*;
import jnr.ffi.Runtime;
import jnr.ffi.types.dev_t;
import jnr.ffi.types.gid_t;
import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import jnr.ffi.types.u_int32_t;
import jnr.ffi.types.uid_t;
import io.f1r3fly.f1r3drive.fuse.struct.Flock;
import io.f1r3fly.f1r3drive.fuse.struct.FuseBuf;
import io.f1r3fly.f1r3drive.fuse.struct.FuseBufvec;
import io.f1r3fly.f1r3drive.fuse.struct.Statvfs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FuseStubFS extends AbstractFuseFS {

    private static final Logger LOGGER = LoggerFactory.getLogger(F1r3flyFuse.class);

    @Override
    public int getattr(String path, FileStat stat) {
        LOGGER.trace("Called getattr for path: {}", path);
        return 0;
    }

    @Override
    public int readlink(String path, Pointer buf, @size_t long size) {
        LOGGER.trace("Called readlink for path: {}", path);
        return 0;
    }

    @Override
    public int mknod(String path, @mode_t long mode, @dev_t long rdev) {
        LOGGER.trace("Called mknod for path: {}", path);
        return create(path, mode, null);
    }

    @Override
    public int mkdir(String path, @mode_t long mode) {
        LOGGER.trace("Called mkdir for path: {}", path);
        return 0;
    }

    @Override
    public int unlink(String path) {
        LOGGER.trace("Called unlink for path: {}", path);
        return 0;
    }

    @Override
    public int rmdir(String path) {
        LOGGER.trace("Called rmdir for path: {}", path);
        return 0;
    }

    @Override
    public int symlink(String oldpath, String newpath) {
        LOGGER.trace("Called symlink for path: {} -> {}", oldpath, newpath);
        return 0;
    }

    @Override
    public int rename(String oldpath, String newpath) {
        LOGGER.trace("Called rename for path: {} -> {}", oldpath, newpath);
        return 0;
    }

    @Override
    public int link(String oldpath, String newpath) {
        LOGGER.trace("Called link for path: {} -> {}", oldpath, newpath);
        return 0;
    }

    @Override
    public int chmod(String path, @mode_t long mode) {
        LOGGER.trace("Called chmod for path: {}", path);
        return 0;
    }

    @Override
    public int chown(String path, @uid_t long uid, @gid_t long gid) {
        LOGGER.trace("Called chown for path: {}", path);
        return 0;
    }

    @Override
    public int truncate(String path, @off_t long size) {
        LOGGER.trace("Called truncate for path: {}", path);
        return 0;
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        LOGGER.trace("Called open for path: {}", path);
        return 0;
    }

    @Override
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        LOGGER.trace("Called read for path: {}", path);
        return 0;
    }

    @Override
    public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        LOGGER.trace("Called write for path: {}", path);
        return 0;
    }

    @Override
    public int statfs(String path, Statvfs stbuf) {
        LOGGER.trace("Called statfs for path: {}", path);
        return 0;
    }

    @Override
    public int flush(String path, FuseFileInfo fi) {
        LOGGER.trace("Called flush for path: {}", path);
        return 0;
    }

    @Override
    public int release(String path, FuseFileInfo fi) {
        LOGGER.trace("Called release for path: {}", path);
        return 0;
    }

    @Override
    public int fsync(String path, int isdatasync, FuseFileInfo fi) {
        LOGGER.trace("Called fsync for path: {}", path);
        return 0;
    }

    @Override
    public int setxattr(String path, String name, Pointer value, @size_t long size, int flags) {
        LOGGER.trace("Called setxattr for path: {}", path);
        return 0;
    }

    @Override
    public int getxattr(String path, String name, Pointer value, @size_t long size) {
        LOGGER.trace("Called getxattr for path: {}", path);
        return 0;
    }

    @Override
    public int listxattr(String path, Pointer list, @size_t long size) {
        LOGGER.trace("Called listxattr for path: {}", path);
        return 0;
    }

    @Override
    public int removexattr(String path, String name) {
        LOGGER.trace("Called removexattr for path: {}", path);
        return 0;
    }

    @Override
    public int opendir(String path, FuseFileInfo fi) {
        LOGGER.trace("Called opendir for path: {}", path);
        return 0;
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filter, @off_t long offset, FuseFileInfo fi) {
        LOGGER.trace("Called readdir for path: {}", path);
        return 0;
    }

    @Override
    public int releasedir(String path, FuseFileInfo fi) {
        LOGGER.trace("Called releasedir for path: {}", path);
        return 0;
    }

    @Override
    public int fsyncdir(String path, FuseFileInfo fi) {
        LOGGER.trace("Called fsyncdir for path: {}", path);
        return 0;
    }

    @Override
    public Pointer init(Pointer conn) {
        LOGGER.trace("Called init");
        return null;
    }

    @Override
    public void destroy(Pointer initResult) {
        LOGGER.trace("Called destroy");
    }

    @Override
    public int access(String path, int mask) {
        LOGGER.trace("Called access for path: {}", path);
        return 0;
    }

    @Override
    public int create(String path, @mode_t long mode, FuseFileInfo fi) {
        LOGGER.trace("Called create for path: {}", path);
        return 0; //-ErrorCodes.ENOSYS();
    }

    @Override
    public int ftruncate(String path, @off_t long size, FuseFileInfo fi) {
        LOGGER.trace("Called ftruncate for path: {}", path);
        return truncate(path, size);
    }

    @Override
    public int fgetattr(String path, FileStat stbuf, FuseFileInfo fi) {
        LOGGER.trace("Called fgetattr for path: {}", path);
        return getattr(path, stbuf);
    }

    @Override
    public int lock(String path, FuseFileInfo fi, int cmd, Flock flock) {
        LOGGER.trace("Called lock for path: {}", path);
        return 0; // -ErrorCodes.ENOSYS();
    }

    @Override
    public int utimens(String path, Timespec[] timespec) {
        LOGGER.trace("Called utimens for path: {}", path);
        return 0; // -ErrorCodes.ENOSYS();
    }

    @Override
    public int bmap(String path, @size_t long blocksize, long idx) {
        LOGGER.trace("Called bmap for path: {}", path);
        return 0;
    }

    @Override
    public int ioctl(String path, int cmd, Pointer arg, FuseFileInfo fi, @u_int32_t long flags, Pointer data) {
        LOGGER.trace("Called ioctl for path: {}", path);
        return 0; // -ErrorCodes.ENOSYS();
    }

    @Override
    public int poll(String path, FuseFileInfo fi, FusePollhandle ph, Pointer reventsp) {
        LOGGER.trace("Called poll for path: {}", path);
        return 0; // -ErrorCodes.ENOSYS();
    }

    @Override
    public int write_buf(String path, FuseBufvec buf, @off_t long off, FuseFileInfo fi) {
//        LOGGER.debug("Called write_buf for path: " + mountPoint.toAbsolutePath() + path);
        // TODO.
        // Some problem in implementation, but it not enabling by default
        int res;
        int size = (int) libFuse.fuse_buf_size(buf);
        FuseBuf flatbuf;
        FuseBufvec tmp = new FuseBufvec(Runtime.getSystemRuntime());
        long adr = MemoryIO.getInstance().allocateMemory(Struct.size(tmp), false);
        tmp.useMemory(Pointer.wrap(Runtime.getSystemRuntime(), adr));
        FuseBufvec.init(tmp, size);
        long mem = 0;
        if (buf.count.get() == 1 && buf.buf.flags.get() == FuseBufFlags.FUSE_BUF_IS_FD) {
            flatbuf = buf.buf;
        } else {
            res = -ErrorCodes.ENOMEM();
            mem = MemoryIO.getInstance().allocateMemory(size, false);
            if (mem == 0) {
                MemoryIO.getInstance().freeMemory(adr);
                return res;
            }
            tmp.buf.mem.set(mem);
            res = (int) libFuse.fuse_buf_copy(tmp, buf, 0);
            if (res <= 0) {
                MemoryIO.getInstance().freeMemory(adr);
                MemoryIO.getInstance().freeMemory(mem);
                return res;
            }
            tmp.buf.size.set(res);
            flatbuf = tmp.buf;
        }
        res = write(path, flatbuf.mem.get(), flatbuf.size.get(), off, fi);
        if (mem != 0) {
            MemoryIO.getInstance().freeMemory(adr);
            MemoryIO.getInstance().freeMemory(mem);
        }
        return res;
    }

    @Override
    public int read_buf(String path, Pointer bufp, @size_t long size, @off_t long off, FuseFileInfo fi) {
        LOGGER.trace("Called read_buf for path: {}", path);
        // should be implemented or null
        long vecmem = MemoryIO.getInstance().allocateMemory(Struct.size(new FuseBufvec(Runtime.getSystemRuntime())), false);
        if (vecmem == 0) {
            return -ErrorCodes.ENOMEM();
        }
        Pointer src = Pointer.wrap(Runtime.getSystemRuntime(), vecmem);
        long memAdr = MemoryIO.getInstance().allocateMemory(size, false);
        if (memAdr == 0) {
            MemoryIO.getInstance().freeMemory(vecmem);
            return -ErrorCodes.ENOMEM();
        }
        Pointer mem = Pointer.wrap(Runtime.getSystemRuntime(), memAdr);
        FuseBufvec buf = FuseBufvec.of(src);
        FuseBufvec.init(buf, size);
        buf.buf.mem.set(mem);
        bufp.putAddress(0, src.address());
        int res = read(path, mem, size, off, fi);
        if (res >= 0)
            buf.buf.size.set(res);
        return res;
    }

    @Override
    public int flock(String path, FuseFileInfo fi, int op) {
        LOGGER.trace("Called flock for path: {}", path);
        return 0; // -ErrorCodes.ENOSYS();
    }

    @Override
    public int fallocate(String path, int mode, @off_t long off, @off_t long length, FuseFileInfo fi) {
        LOGGER.trace("Called fallocate for path: {}", path);
        return 0; // -ErrorCodes.ENOSYS();
    }
}
