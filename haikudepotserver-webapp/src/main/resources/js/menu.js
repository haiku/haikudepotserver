
const HDSMenu = {

    initialize: function() {

        const KLASS_MENU_MODAL = "menu-modal";
        const EL_ID_MENU_TOGGLE = "header-banner-menu-toggle"

        /**
         * @returns {boolean}
         */
        function toggleMenu() {
            const bodyEls = document.getElementsByTagName("body");

            if (bodyEls) {
                HDS.toggleKlass(bodyEls[0], KLASS_MENU_MODAL);
            } else {
                console.error("unable to find body element");
            }

            return true;
        }

        const menuToggleEl = document.getElementById(EL_ID_MENU_TOGGLE);

        if (menuToggleEl) {
            menuToggleEl.addEventListener("click", toggleMenu);
        } else {
            console.error(`element #${EL_ID_MENU_TOGGLE} not found`);
        }
    }
}

document.addEventListener("DOMContentLoaded", HDSMenu.initialize);