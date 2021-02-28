package redis.embedded.model;

import static redis.embedded.model.Architecture.x86;
import static redis.embedded.model.Architecture.x86_64;
import static redis.embedded.model.OS.WINDOWS;

public class OsArchitecture {
    
    public static final OsArchitecture WINDOWS_x86 = new OsArchitecture(WINDOWS, x86);
    public static final OsArchitecture WINDOWS_x86_64 = new OsArchitecture(WINDOWS, x86_64);
    
    public static final OsArchitecture UNIX_x86 = new OsArchitecture(OS.UNIX, x86);
    public static final OsArchitecture UNIX_x86_64 = new OsArchitecture(OS.UNIX, x86_64);
    
    public static final OsArchitecture MAC_OS_X_x86 = new OsArchitecture(OS.MAC_OS_X, x86);
    public static final OsArchitecture MAC_OS_X_x86_64 = new OsArchitecture(OS.MAC_OS_X, x86_64);

    private final OS os;
    private final Architecture arch;
    
    public static OsArchitecture detect() {
        OS os = OS.detectOS();
        Architecture arch = os.detectArchitecture();
        return new OsArchitecture(os, arch);
    }

    public OsArchitecture(final OS os, final Architecture arch) {
        this.os = os;
        this.arch = arch;
    }
    
    public OS os() {
        return os;
    }

    public Architecture arch() {
        return arch;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OsArchitecture that = (OsArchitecture) o;

        return arch == that.arch && os == that.os;
    }

    @Override
    public int hashCode() {
        int result = os.hashCode();
        result = 31 * result + arch.hashCode();
        return result;
    }

}
