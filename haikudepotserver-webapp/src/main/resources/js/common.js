/**
 * Provides a bunch of helpers for the HDS user interface. This will be just very basic HTML
 * manipulation functionality; the bulk of the application is server-side render.
 */

const HDS = {

    /**
     * Sets up all the common behaviors such as toggle disclosures.
     */
    initialize: function() {

        const KLASS_DISCLOSURE_SHOWN = "shown";

        function toggleDisclosure(el) {
            HDS.toggleKlass(el, KLASS_DISCLOSURE_SHOWN);

            // There may be a hidden input name; this should be populated with the
            // current "shown" state.

            const inputName = el.getAttribute("data-shown-input-name");

            if (inputName) {
                const inputEl = Array.from(el.getElementsByTagName("input"))
                    .filter(el => el.getAttribute("type").toLowerCase() === "hidden")
                    .filter(el => el.getAttribute("name") === inputName)
                    [0];

                if (!inputEl) {
                    console.error(`unable to find the hidden input [${inputName}]`)
                } else {
                    inputEl.setAttribute("value", "" + HDS.hasKlass(el, KLASS_DISCLOSURE_SHOWN));
                }
            }
        }

        Array.from(document.getElementsByClassName("common-disclosure"))
            .forEach(containerEl => {
                Array.from(containerEl.children).forEach((containerChildEl) => {
                    if (containerChildEl.localName.toLowerCase() === "svg") {
                        containerChildEl.addEventListener("click", () => toggleDisclosure(containerEl));
                    }
                });
            })
    },

    /**
     * This function will obtain the class names on an element.
     * @param {Element} el
     * @returns {Array[string]}
     */
    extractKlasses : function(el) {
       if (!el) {
           return [];
       }
       return el.className ? [... new Set(el.className.split(/\s+/))].sort() : [];
    },

    /**
     * If the supplied class name is on the element then remove it. If it is not on the element then
     * add it in.
     * @param {Element} el
     * @param {string} klass
     */
    toggleKlass : function(el, klass) {
        const klasses = HDS.extractKlasses(el);
        if (klasses.indexOf(klass) === -1) {
            el.className = [...klasses, ...[klass]].join(" ");
        } else {
            el.className = klasses.filter(x => x !== klass).join(" ");
        }
    },

    /**
     * @param {Element} el
     * @param {string} klass
     * @returns {boolean} if the class name is present on the Element.
     */
    hasKlass : function(el, klass) {
      return HDS.extractKlasses(el).indexOf(klass) !== -1;
    },

    /**
     * Adds a class name `klass` to an element.
     * @param {Element} el
     * @param {string} klass
     */
    addKlass : function(el, klass) {
        const klasses = HDS.extractKlasses(el);
        if (klasses.indexOf(klass) === -1) {
            el.className = [...klasses, ...[klass]].join(" ");
        }
    },

    /**
     * Remove a class name `klass` from an element.
     * @param {Element} el
     * @param {string} klass
     */
    removeKlass : function(el, klass) {
        const klasses = HDS.extractKlasses(el);
        if (klasses.indexOf(klass) !== -1) {
            el.className = klasses.filter(x => x !== klass).join(" ");
        }
    },

}

document.addEventListener("DOMContentLoaded", HDS.initialize);