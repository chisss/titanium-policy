package com.titanium.policy.valueobject;

public record PolicyNo(String value) {
    // record 自动生成基于 value 的 equals/hashCode，无需手写；仅定制 toString 直接输出保单号
    @Override
    public String toString() {
        return value;
    }
}
