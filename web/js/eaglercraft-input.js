(function() {
    "use strict";

    function getCanvas() {
        return document.getElementById("game-canvas") || document.querySelector("canvas");
    }

    function ensureQueue() {
        window.__eaglerInputEvents = window.__eaglerInputEvents || [];
        return window.__eaglerInputEvents;
    }

    function pushEvent(text) {
        var queue = ensureQueue();
        queue.push(text);
        if (queue.length > 512) {
            queue.shift();
        }
    }

    function getMods(event) {
        return (event.shiftKey ? 1 : 0) | (event.ctrlKey ? 2 : 0) | (event.altKey ? 4 : 0) | (event.metaKey ? 8 : 0);
    }

    function getMousePosition(event) {
        var canvas = getCanvas();
        var rect = canvas
            ? canvas.getBoundingClientRect()
            : { left: 0, top: 0, width: window.innerWidth || 1, height: window.innerHeight || 1 };
        var windowWidth = window.innerWidth || rect.width || 1;
        var windowHeight = window.innerHeight || rect.height || 1;

        if (document.pointerLockElement === canvas) {
            window.__eaglerCursorX += event.movementX || 0;
            window.__eaglerCursorY += event.movementY || 0;
        } else {
            window.__eaglerCursorX = (event.clientX - rect.left) * windowWidth / (rect.width || windowWidth || 1);
            window.__eaglerCursorY = (event.clientY - rect.top) * windowHeight / (rect.height || windowHeight || 1);
        }

        return [window.__eaglerCursorX, window.__eaglerCursorY];
    }

    function mapMouseButton(button) {
        return button === 2 ? 1 : button === 1 ? 2 : button;
    }

    function mapKey(event) {
        var code = event.code || "";
        if (code.indexOf("Key") === 0 && code.length === 4) {
            return code.charCodeAt(3);
        }
        if (code.indexOf("Digit") === 0 && code.length === 6) {
            return 48 + code.charCodeAt(5) - 48;
        }
        if (code.indexOf("Numpad") === 0 && code.length === 7) {
            var number = code.charCodeAt(6) - 48;
            if (number >= 0 && number <= 9) {
                return 320 + number;
            }
        }
        if (code.indexOf("Arrow") === 0) {
            return code === "ArrowRight" ? 262 : code === "ArrowLeft" ? 263 : code === "ArrowDown" ? 264 : 265;
        }
        if (code.indexOf("Shift") === 0) {
            return code === "ShiftRight" ? 344 : 340;
        }
        if (code.indexOf("Control") === 0) {
            return code === "ControlRight" ? 345 : 341;
        }
        if (code.indexOf("Alt") === 0) {
            return code === "AltRight" ? 346 : 342;
        }
        if (code.indexOf("Meta") === 0) {
            return code === "MetaRight" ? 347 : 343;
        }
        if (code.indexOf("F") === 0) {
            var functionKey = +code.substring(1);
            if (functionKey >= 1 && functionKey <= 25) {
                return 289 + functionKey;
            }
        }

        switch (code) {
            case "Space": return 32;
            case "Enter": return 257;
            case "Tab": return 258;
            case "Backspace": return 259;
            case "Insert": return 260;
            case "Delete": return 261;
            case "PageUp": return 266;
            case "PageDown": return 267;
            case "Home": return 268;
            case "End": return 269;
            case "Escape": return 256;
            case "CapsLock": return 280;
            case "ScrollLock": return 281;
            case "NumLock": return 282;
            case "PrintScreen": return 283;
            case "Pause": return 284;
            case "Minus": return 45;
            case "Equal": return 61;
            case "BracketLeft": return 91;
            case "BracketRight": return 93;
            case "Backslash": return 92;
            case "Semicolon": return 59;
            case "Quote": return 39;
            case "Comma": return 44;
            case "Period": return 46;
            case "Slash": return 47;
            case "Backquote": return 96;
            case "NumpadDecimal": return 330;
            case "NumpadDivide": return 331;
            case "NumpadMultiply": return 332;
            case "NumpadSubtract": return 333;
            case "NumpadAdd": return 334;
            case "NumpadEnter": return 335;
            case "NumpadEqual": return 336;
            default: return -1;
        }
    }

    function preventGameKey(event) {
        return document.pointerLockElement || event.code === "Tab" || event.code === "Space" || event.code.indexOf("Arrow") === 0;
    }

    function hookCanvas() {
        var canvas = getCanvas();
        if (!canvas || canvas.__eaglerInputHooked) {
            return;
        }

        canvas.__eaglerInputHooked = true;
        canvas.tabIndex = 0;
        canvas.style.touchAction = "none";
        canvas.addEventListener("contextmenu", function(event) {
            event.preventDefault();
        });
        canvas.addEventListener("mousemove", function(event) {
            var pos = getMousePosition(event);
            pushEvent("1," + pos[0] + "," + pos[1]);
        }, { passive: false });
        canvas.addEventListener("mousedown", function(event) {
            var pos = getMousePosition(event);
            if (canvas.focus) {
                canvas.focus();
            }
            pushEvent("1," + pos[0] + "," + pos[1]);
            pushEvent("2," + mapMouseButton(event.button || 0) + ",1," + getMods(event));
            event.preventDefault();
        }, { passive: false });
        canvas.addEventListener("mouseup", function(event) {
            var pos = getMousePosition(event);
            pushEvent("1," + pos[0] + "," + pos[1]);
            pushEvent("2," + mapMouseButton(event.button || 0) + ",0," + getMods(event));
            event.preventDefault();
        }, { passive: false });
        canvas.addEventListener("wheel", function(event) {
            var pos = getMousePosition(event);
            pushEvent("1," + pos[0] + "," + pos[1]);
            pushEvent("3," + -Math.sign(event.deltaX || 0) + "," + -Math.sign(event.deltaY || 0));
            event.preventDefault();
        }, { passive: false });
        canvas.addEventListener("mouseenter", function() {
            pushEvent("7,1");
        });
        canvas.addEventListener("mouseleave", function() {
            pushEvent("7,0");
        });
    }

    window.__eaglerInstallInputBridge = function() {
        if (typeof document === "undefined") {
            return;
        }

        ensureQueue();
        window.__eaglerCursorX = window.__eaglerCursorX || (window.innerWidth || 854) / 2;
        window.__eaglerCursorY = window.__eaglerCursorY || (window.innerHeight || 480) / 2;
        window.__eaglerHookInputCanvas = hookCanvas;

        if (window.__eaglerInputInstalled) {
            hookCanvas();
            return;
        }

        window.__eaglerInputInstalled = true;
        hookCanvas();

        window.addEventListener("keydown", function(event) {
            var key = mapKey(event);
            if (key === -1) {
                return;
            }

            pushEvent("4," + key + "," + (event.keyCode || 0) + "," + (event.repeat ? 2 : 1) + "," + getMods(event));
            if (event.key && event.key.length === 1 && !event.ctrlKey && !event.metaKey) {
                pushEvent("5," + event.key.charCodeAt(0) + "," + getMods(event));
            }
            if (preventGameKey(event)) {
                event.preventDefault();
            }
        }, { passive: false });

        window.addEventListener("keyup", function(event) {
            var key = mapKey(event);
            if (key === -1) {
                return;
            }

            pushEvent("4," + key + "," + (event.keyCode || 0) + ",0," + getMods(event));
            if (preventGameKey(event)) {
                event.preventDefault();
            }
        }, { passive: false });

        window.addEventListener("focus", function() {
            pushEvent("6,1");
        });
        window.addEventListener("blur", function() {
            pushEvent("6,0");
        });
    };
})();
