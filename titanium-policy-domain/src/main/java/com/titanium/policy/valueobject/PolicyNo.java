package com.titanium.policy.valueobject;

import java.util.Objects;

public record PolicyNo(String value) {
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        PolicyNo that = (PolicyNo) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public String toString() {
        return value;
    }
}