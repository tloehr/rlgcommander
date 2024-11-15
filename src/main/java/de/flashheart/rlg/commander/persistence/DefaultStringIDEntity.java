package de.flashheart.rlg.commander.persistence;

import jakarta.persistence.*;
import java.io.Serializable;

@MappedSuperclass
public abstract class DefaultStringIDEntity implements Serializable {
    private String id;
    private int version;

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.AUTO)
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Version
    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    @Override
    public int hashCode() {
        if (id == null) {
            return super.hashCode();
        }

        return 31 + id.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        boolean equal;

        if (id == null) {
            // New entities are only equal if the instance if the same
            equal = super.equals(other);
        } else if (this == other) {
            equal = true;
        } else if (!(other instanceof DefaultStringIDEntity)) {
            equal = false;
        } else {
            equal = id.equals(((DefaultStringIDEntity) other).id);
        }

//        log.debug("this: " + toString() + " other: " + Tools.catchNull(other, "null") + ": is equal ??: " + Boolean.toString(equal));
        return equal;
    }
}
