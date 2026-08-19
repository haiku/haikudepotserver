const HDSPkgList = {

    initialize: function () {

        const VALUE_CATEGORIES = "CATEGORIES";
        const EL_ID_FILTERS_CATEGORY_CONTAINER = "pkg-list-filters-category-container";
        const EL_ID_FILTERS_SORTING_SELECTOR = "pkg-list-filters-sorting-selector";

        function initializeSortingSelector() {

            /**
             * @param event {Event}
             * @returns {boolean}
             */
            function onChange(event) {
                const categoryContainerEl = document.getElementById(EL_ID_FILTERS_CATEGORY_CONTAINER);

                if (event.target.value === VALUE_CATEGORIES) {
                    HDS.removeKlass(categoryContainerEl, "common-hidden");
                } else {
                    HDS.addKlass(categoryContainerEl, "common-hidden");
                }

                return true;
            }

            const sortingSelectorEl = document.getElementById(EL_ID_FILTERS_SORTING_SELECTOR);
            sortingSelectorEl.addEventListener("change", onChange)
        }

        initializeSortingSelector();
    }
}

document.addEventListener("DOMContentLoaded", HDSPkgList.initialize);