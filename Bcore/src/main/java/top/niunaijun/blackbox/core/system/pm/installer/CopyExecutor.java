package top.niunaijun.blackbox.core.system.pm.installer;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.core.system.pm.BPackageSettings;
import top.niunaijun.blackbox.entity.pm.InstallOption;
import top.niunaijun.blackbox.utils.NativeUtils;


public class CopyExecutor implements Executor {

    @Override
    public int exec(BPackageSettings ps, InstallOption option, int userId) {
        try {
            if (!option.isFlag(InstallOption.FLAG_SYSTEM)) {
                NativeUtils.copyNativeLib(new File(ps.pkg.baseCodePath), BEnvironment.getAppLibDir(ps.pkg.packageName));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
        if (option.isFlag(InstallOption.FLAG_STORAGE)) {
            
            File origFile = new File(ps.pkg.baseCodePath);
            File newFile = BEnvironment.getBaseApkDir(ps.pkg.packageName);
            try {
                copyCodeFile(origFile, newFile);
                if (option.isFlag(InstallOption.FLAG_URI_FILE)
                        && !origFile.getCanonicalFile().equals(newFile.getCanonicalFile())) {
                    // FLAG_URI_FILE sources are installer-owned temporary files. The old rename
                    // path consumed them on success, so preserve that cleanup behavior.
                    origFile.delete();
                }
                
                ps.pkg.baseCodePath = newFile.getAbsolutePath();
            } catch (IOException e) {
                e.printStackTrace();
                return -1;
            }
        } else if (option.isFlag(InstallOption.FLAG_SYSTEM)) {
            
        }
        return 0;
    }

    /**
     * Copies code into a new, read-only inode. Android 14 rejects writable files used for
     * dynamic code loading, so the destination must become read-only after it is opened and
     * before any bytes are written through the already-open descriptor.
     */
    private static void copyCodeFile(File source, File target) throws IOException {
        if (source.getCanonicalFile().equals(target.getCanonicalFile())) {
            if (target.canWrite() && !target.setReadOnly()) {
                throw new IOException("Unable to make code file read-only: " + target);
            }
            return;
        }

        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create code directory: " + parent);
        }
        if (target.exists()) {
            target.setWritable(true, true);
            if (!target.delete()) {
                throw new IOException("Unable to replace code file: " + target);
            }
        }

        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            if (!target.setReadOnly()) {
                throw new IOException("Unable to make code file read-only: " + target);
            }

            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.getFD().sync();
        } catch (IOException e) {
            target.setWritable(true, true);
            target.delete();
            throw e;
        }
    }
}
