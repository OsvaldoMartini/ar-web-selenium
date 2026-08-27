(() => {
  const search = document.querySelector('[data-guide-search]');
  const count = document.querySelector('[data-search-count]');
  const sections = [...document.querySelectorAll('.document > h1, .document > h2')];
  const navLinks = [...document.querySelectorAll('.sidebar a[href^="#"]')];

  const updateActive = () => {
    let active = sections[0];
    const line = window.scrollY + 120;
    for (const section of sections) {
      if (section.offsetTop <= line) active = section;
    }
    navLinks.forEach(link => link.classList.toggle('active', active && link.hash === `#${active.id}`));
  };

  const runSearch = () => {
    const query = String(search?.value || '').trim().toLocaleLowerCase();
    let matches = 0;
    for (const heading of sections) {
      const nodes = [heading];
      let next = heading.nextElementSibling;
      while (next && !/^H[12]$/.test(next.tagName)) {
        nodes.push(next);
        next = next.nextElementSibling;
      }
      const hit = !query || nodes.some(node => node.textContent.toLocaleLowerCase().includes(query));
      nodes.forEach(node => node.classList.toggle('search-hidden', !hit));
      if (query && hit) matches += 1;
    }
    if (count) count.textContent = query ? `${matches} section${matches === 1 ? '' : 's'}` : 'All sections';
  };

  search?.addEventListener('input', runSearch);
  window.addEventListener('scroll', updateActive, { passive: true });
  updateActive();
})();
