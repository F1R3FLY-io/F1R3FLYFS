package io.f1r3fly.fs;

import com.kenai.jffi.MemoryIO;
import io.f1r3fly.fs.examples.F1r3flyFS;
import io.f1r3fly.fs.flags.FuseBufFlags;
import io.f1r3fly.fs.struct.FileStat;
import io.f1r3fly.fs.struct.FuseFileInfo;
import io.f1r3fly.fs.struct.FusePollhandle;
import io.f1r3fly.fs.struct.Timespec;
import jnr.ffi.*;
import jnr.ffi.Runtime;
import jnr.ffi.types.dev_t;
import jnr.ffi.types.gid_t;
import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import jnr.ffi.types.u_int32_t;
import jnr.ffi.types.uid_t;
import io.f1r3fly.fs.struct.Flock;
import io.f1r3fly.fs.struct.FuseBuf;
import io.f1r3fly.fs.struct.FuseBufvec;
import io.f1r3fly.fs.struct.Statvfs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FuseStubFS extends AbstractFuseFS {

    private static final Logger LOGGER = LoggerFactory.getLogger(F1r3flyFS.class);

    @Override
    public int getattr(String path, FileStat stat) {
        LOGGER.debug("Called getattr for path: " + mountPoint.toAbsolutePath() + path);
        return 0;
    }

    @Override
    public int readlink(String path, Pointer buf, @size_t long size) {
        LOGGER.debug("Called readlink for path: " + mountPoint.toAbsolutePath() + path);
        return 0;
    }

    @Override
    public int mknod(String path, @mode_t long mode, @dev_t long rdev) {
        LOGGER.debug("Called mknod for path: " + mountPoint.toAbsolutePath() + path);
        return create(path, mode, null);
    }

    @Override
    public int mkdir(String path, @mode_t long mode) {
        LOGGER.debug("Called mkdir for path: " + mountPoint.toAbsolutePath() + path);
        return 0;
    }

    @Override
    public int unlink(String path) {
        LOGGER.debug("Called unlink for path: " + mountPoint.toAbsolutePath() + path);
        return 0;
    }

    @Override
    public int rmdir(String path) {
        LOGGER.debug("Called rmdir for path: " + mountPoint.toAbsolutePath() + path);
        return 0;
    }

    @Override
    public int symlink(String oldpath, String newpath) {
        LOGGER.debug("Called symlink for path: " + oldpath + " -> " + newpath);
        return 0;
    }

    @Override
    public int rename(String oldpath, String newpath) {
        LOGGER.debug("Called rename for path: " + oldpath + " -> " + newpath);
        return 0;
    }

    @Override
    public int link(String oldpath, String newpath) {
        LOGGER.debug("Called link for path: " + oldpath + " -> " + newpath);
        return 0;
    }

    @Override
    public int chmod(String path, @mode_t long mode) {
        LOGGER.debug("Called chmod for path: " + mountPoint.toAbsolutePath() + path);
        return 0;
    }

    @Override
    public int chown(String path, @uid_t long uid, @gid_t long gid) {
        LOGGER.debug("Called chown for path: " + mountPoint.toAbsolutePath() + path);
        return 0;
    }

    @Override
    public int truncate(String path, @off_t long size) {
        LOGGER.debug("Called truncate for path: " + mountPoint.toAbsolutePath() + path);
        return 0;
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        LOGGER.debug("Called open for path: " + mountPoint.toAbsolutePath() + path);
        return 0;
    }

    @Override
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        LOGGER.debug("Called read for path: " + mountPoint.toAbsolutePath() + path);
        return 0;
    }

    @Override
    public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        LOGGER.debug("Called write for path: " + mountPoint.toAbsolutePath() + path);
        return 0;
    }

    @Override
    public int statfs(String path, Statvfs stbuf) {
        LOGGER.debug("Called statfs for path: " + mountPoint.toAbsolutePath() + path);
        return 0;
    }

    @Override
    public int flush(String path, FuseFileInfo fi) {
        LOGGER.debug("Called flush for path: " + mountPoint.toAbsolutePath() + path);
        return 0;
    }

    @Override
    public int release(String path, FuseFileInfo fi) {
        LOGGER.debug("Called release for path: " + mountPoint.toAbsolutePath() + path);
        return 0;
    }

    @Override
    public int fsync(String path, int isdatasync, FuseFileInfo fi) {
        LOGGER.debug("Called fsync for path: " + mountPoint.toAbsolutePath() + path);
        return 0;
    }

    @Override
    public int setxattr(String path, String name, Pointer value, @size_t long size, int flags) {
        LOGGER.debug("Called setxattr for path: " + mountPoint.toAbsolutePath() + path);
        return 0;
    }

    @Override
    public int getxattr(String path, String name, Pointer value, @size_t long size) {
        LOGGER.debug("Called getxattr for path: " + mountPoint.toAbsolutePath() + path);
        return 0;
    }

    @Override
    public int listxattr(String path, Pointer list, @size_t long size) {
        LOGGER.debug("Called listxattr for path: " + mountPoint.toAbsolutePath() + path);
        return 0;
    }

    @Override
    public int removexattr(String path, String name) {
        LOGGER.debug("Called removexattr for path: " + mountPoint.toAbsolutePath() + path);
        return 0;
    }

    @Override
    public int opendir(String path, FuseFileInfo fi) {
        LOGGER.debug("Called opendir for path: " + mountPoint.toAbsolutePath() + path);
        return 0;
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filter, @off_t long offset, FuseFileInfo fi) {
        LOGGER.debug("Called readdir for path: " + mountPoint.toAbsolutePath() + path);
        return 0;
    }

    @Override
    public int releasedir(String path, FuseFileInfo fi) {
        LOGGER.debug("Called releasedir for path: " + mountPoint.toAbsolutePath() + path);
        return 0;
    }

    @Override
    public int fsyncdir(String path, FuseFileInfo fi) {
        LOGGER.debug("Called fsyncdir for path: " + mountPoint.toAbsolutePath() + path);
        return 0;
    }

    @Override
    public Pointer init(Pointer conn) {
        LOGGER.debug("Called init");
        return null;
    }

    @Override
    public void destroy(Pointer initResult) {
        LOGGER.debug("Called destroy");
    }

    @Override
    public int access(String path, int mask) {
        LOGGER.debug("Called access for path: " + mountPoint.toAbsolutePath() + path);
        return 0;
    }

    @Override
    public int create(String path, @mode_t long mode, FuseFileInfo fi) {
        LOGGER.debug("Called create for path: " + mountPoint.toAbsolutePath() + path);
        return 0; //-ErrorCodes.ENOSYS();
    }

    @Override
    public int ftruncate(String path, @off_t long size, FuseFileInfo fi) {
        LOGGER.debug("Called ftruncate for path: " + mountPoint.toAbsolutePath() + path);
        return truncate(path, size);
    }

    @Override
    public int fgetattr(String path, FileStat stbuf, FuseFileInfo fi) {
        LOGGER.debug("Called fgetattr for path: " + mountPoint.toAbsolutePath() + path);
        return getattr(path, stbuf);
    }

    @Override
    public int lock(String path, FuseFileInfo fi, int cmd, Flock flock) {
        LOGGER.debug("Called lock for path: " + mountPoint.toAbsolutePath() + path);
        return 0; // -ErrorCodes.ENOSYS();
    }

    @Override
    public int utimens(String path, Timespec[] timespec) {
        LOGGER.debug("Called utimens for path: " + mountPoint.toAbsolutePath() + path);
        return 0; // -ErrorCodes.ENOSYS();
    }

    @Override
    public int bmap(String path, @size_t long blocksize, long idx) {
        LOGGER.debug("Called bmap for path: " + mountPoint.toAbsolutePath() + path);
        return 0;
    }

    @Override
    public int ioctl(String path, int cmd, Pointer arg, FuseFileInfo fi, @u_int32_t long flags, Pointer data) {
        LOGGER.debug("Called ioctl for path: " + mountPoint.toAbsolutePath() + path);
        return 0; // -ErrorCodes.ENOSYS();
    }

    @Override
    public int poll(String path, FuseFileInfo fi, FusePollhandle ph, Pointer reventsp) {
        LOGGER.debug("Called poll for path: " + mountPoint.toAbsolutePath() + path);
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
        LOGGER.trace("Called read_buf for path: " + mountPoint.toAbsolutePath() + path);
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
        LOGGER.debug("Called flock for path: " + mountPoint.toAbsolutePath() + path);
        return 0; // -ErrorCodes.ENOSYS();
    }

    @Override
    public int fallocate(String path, int mode, @off_t long off, @off_t long length, FuseFileInfo fi) {
        LOGGER.debug("Called fallocate for path: " + mountPoint.toAbsolutePath() + path);
        return 0; // -ErrorCodes.ENOSYS();
    }
}
