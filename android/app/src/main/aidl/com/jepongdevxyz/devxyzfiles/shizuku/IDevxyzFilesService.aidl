// IDevxyzFilesService.aidl — runs inside the Shizuku user-service process
// as shell (UID 2000), so plain java.io.File calls here can see
// /storage/emulated/0/Android/data and /storage/emulated/0/Android/obb.
//
// Entries are pipe-encoded "name|isDir|size|lastModified" rows so the AIDL
// surface stays to plain String arrays — no Parcelable, no file-descriptor
// plumbing. Copy/move/delete all run inside the privileged process, which
// keeps big OBB copies off the Binder transaction buffer.
package com.jepongdevxyz.devxyzfiles.shizuku;

interface IDevxyzFilesService {
    // Returns one pipe-encoded row per child, or null when the directory
    // cannot be listed (missing, not a directory, or denied).
    String[] list(String path);

    // One pipe-encoded row ("name|isDir|size|lastModified") or "" when missing.
    String stat(String path);

    boolean exists(String path);
    boolean canRead(String path);
    boolean mkdir(String path);
    boolean mkdirs(String path);

    // Recursive. Returns false when any part fails.
    boolean delete(String path);

    boolean rename(String oldPath, String newPath);

    // Recursive copy / move, performed entirely in the privileged process.
    boolean copy(String srcPath, String destPath);
    boolean move(String srcPath, String destPath);

    // Copy progress for large files (MLBB OBBs are GBs): the service tracks
    // bytes copied vs total bytes so the app can poll and show a progress bar.
    // Copy itself still streams in 256 KB chunks — nothing is held in memory.
    long getCopyProgress();
    long getCopyTotal();

    // Who am I running as — "2000" proves shell UID on a non-root phone.
    int getUid();
}
