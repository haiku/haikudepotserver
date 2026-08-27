<%--
Copyright 2026, Andrew Lindesay
Distributed under the terms of the MIT License.
--%>
@import java.util.List
@import org.apache.commons.collections4.CollectionUtils
@import org.haiku.haikudepotserver.multipage.validation.model.ValidationFailure
@import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplier

@param InternationalizationSupplier internationalizationSupplier
@param List<ValidationFailure> failures

<ul class="common-validation-failure-container">
    @for (ValidationFailure failure : CollectionUtils.emptyIfNull(failures))
        <li>${internationalizationSupplier.getMessage(failure.getKey(), failure.getParams())}</li>
    @endfor
</ul>