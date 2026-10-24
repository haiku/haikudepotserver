<%--
Copyright 2026, Andrew Lindesay
Distributed under the terms of the MIT License.
--%>

@import org.apache.commons.lang3.BooleanUtils

@param Boolean value

<span>
    @if (BooleanUtils.isTrue(value))
        &checkmark;
    @else
        &cross;
    @endif
</span>