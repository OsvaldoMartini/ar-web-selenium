(function() {
    var tooltip = document.createElement('div');
    tooltip.id = 'Martini-Is-Awesome';
    tooltip.style.position = 'absolute';
    tooltip.style.backgroundColor = 'rgba(255, 165, 0, 0.5)'; // Slightly opaque light orange
    tooltip.style.border = '1px solid #ccc';
    tooltip.style.padding = '10px';
    tooltip.style.borderRadius = '5px';
    tooltip.style.boxShadow = '0 2px 4px rgba(0, 0, 0, 0.2)';
    tooltip.style.fontFamily = 'Arial, sans-serif';
    tooltip.style.fontSize = '14px';
    tooltip.style.color = '#333';
    tooltip.style.zIndex = '10000'; // Higher z-index
    tooltip.style.display = 'none';
    document.body.appendChild(tooltip);

    function getMartiniAbsoluteXPath(element) {
        if (element === document.body) {
            return '/html/' + element.tagName.toLowerCase();
        }
        var ix = 0;
        var siblings = element.parentNode.childNodes;
        for (var i = 0; i < siblings.length; i++) {
            var sibling = siblings[i];
            if (sibling === element) {
                return getMartiniAbsoluteXPath(element.parentNode) + '/' + element.tagName.toLowerCase() + '[' + (ix + 1) + ']';
            }
            if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {
                ix++;
            }
        }
        return '';
    }

    function getMartiniXPath(element) {
        if (element === document.body) {
            return '/html/body';
        }
        var ix = 0;
        var siblings = element.parentNode ? element.parentNode.childNodes : [];
        for (var i = 0; i < siblings.length; i++) {
            var sibling = siblings[i];
            if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {
                if (sibling === element) {
                    return getMartiniXPath(element.parentNode) + '/' + element.tagName.toLowerCase() + '[' + (ix + 1) + ']';
                }
                ix++;
            }
        }
        return '';
    }

    function getMartiniCustomXPath(element) {
        if (element === document.body) {
            return '/html/' + element.tagName.toLowerCase();
        }
        var className = element.className.split(' ').filter(function(cls) { return !/\\d/.test(cls); }).join('.');
        var tagName = element.tagName.toLowerCase();
        var ix = 0;
        var siblings = element.parentNode.childNodes;
        for (var i = 0; i < siblings.length; i++) {
            var sibling = siblings[i];
            if (sibling === element) {
                var path = getMartiniCustomXPath(element.parentNode) + '/' + tagName;
                if (className) {
                    path += '[contains(@class, "' + className + '")]';
                } else {
                    path += '[' + (ix + 1) + ']';
                }
                return path;
            }
            if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {
                ix++;
            }
        }
        return '';
    }

    function showMartiniTooltip(event) {
        var elementBelowTooltip = document.elementFromPoint(event.clientX, event.clientY);
        window.tagNameTemp = elementBelowTooltip.tagName.toLowerCase();
        window.coordsTemp = elementBelowTooltip.getBoundingClientRect();
        window.coordsTemp = window.coordsTemp.left + ',' + window.coordsTemp.top;
        tooltip.textContent = window.tagNameTemp + '-Coordinates:(' + window.coordsTemp + ')';
        var tooltipWidth = tooltip.offsetWidth;
        var tooltipHeight = tooltip.offsetHeight;
        var left = event.pageX - tooltipWidth / 2;
        var top = event.pageY - tooltipHeight / 2;
        tooltip.style.left = left + 'px';
        tooltip.style.top = top + 'px';
        tooltip.style.display = 'block';
    }

    function hideMartiniTooltip() {
        tooltip.style.display = 'none';
    }

    function handleMartiniClick(event) {
        event.preventDefault();
        event.stopPropagation();
        tooltip.style.display = 'none';
        var elementBelowTooltip = document.elementFromPoint(event.clientX, event.clientY);
        tooltip.style.display = 'block';
        console.log(elementBelowTooltip);
        if (elementBelowTooltip.tagName.toLowerCase() === 'iframe') {
            // If the clicked element is an iframe, get the iframe's XPath
            var iframeXPath = getMartiniXPath(elementBelowTooltip);
            window.iFrameXPath = iframeXPath;
            console.log('Iframe XPath:', iframeXPath);
        } else {
            var xpath = getMartiniXPath(elementBelowTooltip);
            var absoluteXPath = getMartiniAbsoluteXPath(elementBelowTooltip);
            var customXPath = getMartiniCustomXPath(elementBelowTooltip);
            window.currentXPath = xpath;
            window.currentAbsoluteXPath = absoluteXPath;
            window.customXPath = customXPath;
            console.log('Element XPath:', xpath);
        }
        window.attribId = elementBelowTooltip.id || '';
        window.attribName = elementBelowTooltip.name || '';
        window.tagName = elementBelowTooltip.tagName.toLowerCase();
        window.coords = elementBelowTooltip.getBoundingClientRect();
        window.coords = window.coords.left + ',' + window.coords.top;
    }

    window.currentXPath = '';
    window.currentAbsoluteXPath = '';
    window.customXPath = '';
    window.iFrameXPath = '';
    window.attribId = '';
    window.attribName = '';
    window.tagName = '';
    window.coords = '';
    window.tagNameTemp = '';
    window.coordsTemp = '';
    document.addEventListener('mouseover', showMartiniTooltip);
    document.addEventListener('click', handleMartiniClick);
    window.removeClickListener = function() {
        document.removeEventListener('mouseover', showMartiniTooltip);
        document.removeEventListener('click', handleMartiniClick);
    };
})();
