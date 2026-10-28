// ==UserScript==
// @name         4PDA Digest Bridge (для 4pda_digest_mobile.html)
// @namespace    https://github.com/Practician/Bookstore
// @version      6.4.0
// @description  Эмулирует AndroidBridge из приложения Bookstore для работы 4pda_digest_mobile.html напрямую в браузере с Tampermonkey/Violentmonkey
// @match        *://*/*4pda_digest_mobile.html*
// @match        file:///*4pda_digest_mobile.html*
// @grant        GM_xmlhttpRequest
// @connect      4pda.to
// @run-at       document-start
// ==/UserScript==

(function() {
    'use strict';

    let cachedAuth = { loggedIn: true, username: '4PDA Browser' };

    const bridge = {
        getAuthState: function() {
            return JSON.stringify(cachedAuth);
        },
        openForumUrl: function(url) {
            window.open(url || 'https://4pda.to/forum/index.php?act=auth', '_blank');
        },
        logoutFromForum: function() {
            cachedAuth = { loggedIn: false, username: '' };
            if (typeof window.onAuthStateChanged === 'function') {
                window.onAuthStateChanged(false, '');
            }
        },
        fetchForumPage: function(url, callbackId) {
            GM_xmlhttpRequest({
                method: 'GET',
                url: url,
                responseType: 'arraybuffer',
                anonymous: false,
                onload: function(resp) {
                    try {
                        const bytes = new Uint8Array(resp.response);
                        let binary = '';
                        const chunkSize = 0x8000;
                        for (let i = 0; i < bytes.length; i += chunkSize) {
                            binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunkSize));
                        }
                        const b64 = btoa(binary);
                        if (typeof window.onForumPage === 'function') {
                            window.onForumPage(callbackId, resp.status >= 200 && resp.status < 400, b64);
                        }
                    } catch (e) {
                        if (typeof window.onForumPage === 'function') {
                            window.onForumPage(callbackId, false, null);
                        }
                    }
                },
                onerror: function() {
                    if (typeof window.onForumPage === 'function') {
                        window.onForumPage(callbackId, false, null);
                    }
                }
            });
        }
    };

    window.AndroidBridge = bridge;
    try {
        if (typeof unsafeWindow !== 'undefined') {
            unsafeWindow.AndroidBridge = bridge;
        }
    } catch (_) {}
})();
