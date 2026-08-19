const HDSHome = {

    initialize: function () {

        const EL_ID_CATEGORY_SELECT = "home-filters-category-select";

        function initializeCategorySelect() {

            /**
             * @param event {Event}
             * @returns {boolean}
             */
            function onChange(event) {
                event.target.form.submit();
                return true;
            }

            const selectEl = document.getElementById(EL_ID_CATEGORY_SELECT);
            selectEl.addEventListener("change", onChange)
        }

        initializeCategorySelect();
    }
}

document.addEventListener("DOMContentLoaded", HDSHome.initialize);