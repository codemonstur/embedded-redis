package redis.embedded.model;

import static redis.embedded.model.Architecture.x86;
import static redis.embedded.model.Architecture.x86_64;
import static redis.embedded.model.Architecture.aarch64;
import static redis.embedded.model.OS.MAC_OS_X;
import static redis.embedded.model.OS.UNIX;
import static redis.embedded.model.OS.WINDOWS;

/**
 * The {@code OsArchitecture} class.
 */
public class OsArchitecture {
    
    /**
     * The {@code MAC_OS_X_ARM64} constant.
     */
    /**
     * The {@code MAC_OS_X_x86_64} constant.
     */
    /**
     * The {@code UNIX_AARCH64} constant.
     */
    /**
     * The {@code UNIX_x86_64} constant.
     */
    /**
     * The {@code UNIX_x86} constant.
     */
    /**
     * The {@code WINDOWS_x86_64} constant.
     */
    /**
     * The {@code WINDOWS_x86} constant.
     */
    public static final OsArchitecture
        WINDOWS_x86 = new OsArchitecture(WINDOWS, x86),
        WINDOWS_x86_64 = new OsArchitecture(WINDOWS, x86_64),
        UNIX_x86 = new OsArchitecture(UNIX, x86),
        UNIX_x86_64 = new OsArchitecture(UNIX, x86_64),
        UNIX_AARCH64 = new OsArchitecture(UNIX, aarch64),
        MAC_OS_X_x86_64 = new OsArchitecture(MAC_OS_X, x86_64),
        MAC_OS_X_ARM64 = new OsArchitecture(MAC_OS_X, aarch64);

    /**
     * The {@code os} field.
     */
    public final OS os;
    /**
     * The {@code arch} field.
     */
    public final Architecture arch;
    
    /**
     * Returns the result of {@code detectOSandArchitecture}.
     *
     * @return the result
     */
    public static OsArchitecture detectOSandArchitecture() {
        final OS os = OS.detectOS();
        final Architecture arch = os.detectArchitecture();
        return new OsArchitecture(os, arch);
    }

    /**
     * Creates a new instance.
     *
     * @param os the os
     * @param arch the arch
     */
    public OsArchitecture(final OS os, final Architecture arch) {
        this.os = os;
        this.arch = arch;
    }

    /**
     * Compares this object with another object for equality.
     *
     * @param o the o
     * @return whether the condition holds
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final OsArchitecture that = (OsArchitecture) o;
        return arch == that.arch && os == that.os;
    }

    /**
     * Returns the hash code of this object.
     *
     * @return the result
     */
    @Override
    public int hashCode() {
        int result = os.hashCode();
        result = 31 * result + arch.hashCode();
        return result;
    }

}
