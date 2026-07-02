# Script Scanner — Documentação Técnica

> Estado atual, problemas identificados e próximos passos

---

## Estado Atual

### 1. Elementos sem ID ou Atributos Detectáveis

O scanner utiliza como critério secundário o **nome visível do elemento** (ex: "Avançar", "Confirmar") combinado com **propriedades visuais de design** (cor, formato, estilo de botão) para identificar e classificar os componentes da página.

---

### 2. Nomes Repetidos

O problema de elementos com nomes duplicados (ex: `Valuta`, `EUR`, `USD` aparecendo múltiplas vezes na mesma página) foi resolvido através de **sufixação automática**:

```
valuta_1, valuta_2
EUR_1, EUR_2
USD_1, USD_2
```

O algoritmo consegue agora trabalhar de forma determinística com estes casos.

> ⚠️ A lógica de sufixação ainda precisa de refinamento para cenários mais complexos — ver secção **Problemas a Resolver**.

---

### 3. Elementos Não Encontrados pelo Scanner Automático

Para elementos que o Page Scanner não consegue identificar, é utilizado o mecanismo **HOVER PICK**, que permite a captura manual do XPath diretamente no browser.

Infelizmente, este tipo de elemento está a crescer em frequência. O caso mais difícil é o **botão dinâmico sem nenhum atributo estável**:

```html
<div class="flex items-center justify-center rounded-lg bg-blue-500 cursor-pointer">
  <span>Confirmar</span>
</div>
```

As classes CSS mudam a cada update da aplicação. A única estratégia viável é combinar texto visível com tipo de elemento:

```javascript
// XPath
//div[.//span[text()='Confirmar'] and contains(@class,'cursor-pointer')]

// Com Playwright — mais robusto
page.getByText('Confirmar').locator('..')  // sobe para o elemento pai clicável
```

---

### 4. Tabs Dinâmicas sem ID Fixo

Elementos de navegação (tabs) que não possuem identificadores estáveis são tratados pelo critério de **nome visível como segunda prioridade**.

#### Exemplo de HTML que quebra o scanner:

```html
<!-- Versão inicial -->
<ul class="tabs-nav">
  <li class="tab-item active" data-index="0" onclick="switchTab(0)">
    <span>Bonifico</span>
  </li>
  <li class="tab-item" data-index="1" onclick="switchTab(1)">
    <span>Pagamento</span>
  </li>
</ul>

<!-- Após update — data-index desaparece, classe "active" vira "selected" -->
<ul class="navigation-tabs-wrapper">
  <li class="nav-tab selected" onclick="loadTab('bonifico')">Bonifico</li>
  <li class="nav-tab" onclick="loadTab('pagamento')">Pagamento</li>
</ul>
```

#### XPaths e porque falham:

```javascript
// ❌ por id (não existe)
//*[@id='tab-bonifico']

// ❌ por data-index (desapareceu no update)
//li[@data-index='0']

// ❌ por classe "active" (mudou para "selected")
//li[contains(@class,'active')]

// ⚠️ por posição absoluta (frágil — quebra se a ordem mudar)
//ul/li[1]

// ✅ por texto visível (mais estável)
//ul//li[normalize-space(text())='Bonifico']
//ul//li[.//span[text()='Bonifico']]
```

#### Com Playwright — abordagem recomendada:

```javascript
// Por role + texto
await page.getByRole('tab', { name: 'Bonifico' }).click();

// Se não tiver role="tab" declarado:
await page.locator('li').filter({ hasText: 'Bonifico' }).click();

// Verificar qual tab está ativa (por propriedades visuais)
const isActive = await page.locator('li')
  .filter({ hasText: 'Bonifico' })
  .evaluate(el => {
    const style = window.getComputedStyle(el);
    return (
      el.classList.contains('active') ||
      el.classList.contains('selected') ||
      style.borderBottom !== 'none' ||
      style.fontWeight === '700'
    );
  });
```

---

### 5. INPUT Escondido com Label Clicável

> ⚠️ **Caso frequente em Banca Stato.** O scanner detecta o `INPUT` mas ele está oculto — o elemento clicável real é o `LABEL`. Clicar no input não produz nenhum efeito visível.

#### Caso 1 — Checkbox customizado (o mais comum)

```html
<label class="custom-checkbox" for="accept-terms">
  <input type="checkbox" id="accept-terms" style="display:none"/>
  <span class="checkmark"></span>
  Aceito os termos e condições
</label>
```

```javascript
// ❌ O scanner clica no INPUT — está hidden, nada acontece
//*[@id='accept-terms']

// ✅ Clicar no LABEL — este é o elemento real
//label[@for='accept-terms']

// ✅ Se não tiver atributo "for":
//label[contains(@class,'custom-checkbox')]

// Com Playwright:
await page.getByLabel('Aceito os termos e condições').click();
// ou
await page.locator('label.custom-checkbox').click();
```

---

#### Caso 2 — Radio button customizado (ex: seleção de conta ou moeda)

```html
<div class="radio-group">
  <label class="radio-option">
    <input type="radio" name="account-type" value="corrente" style="visibility:hidden"/>
    <span class="radio-circle"></span>
    Conta Corrente
  </label>
  <label class="radio-option">
    <input type="radio" name="account-type" value="poupanca" style="visibility:hidden"/>
    <span class="radio-circle"></span>
    Conta Poupança
  </label>
</div>
```

```javascript
// ❌ Input está invisível — clicar não funciona
//input[@value='corrente']

// ✅ Clicar no label pai que contém o texto
//label[contains(@class,'radio-option') and .//span[text()='Conta Corrente']]

// ✅ Mais genérico — sobe do texto para o label
//label[normalize-space()='Conta Corrente']

// Com Playwright:
await page.getByLabel('Conta Corrente').click();
// ou se o label não estiver associado por "for":
await page.locator('label.radio-option').filter({ hasText: 'Conta Corrente' }).click();
```

---

#### Caso 3 — Toggle/Switch (ex: ativar notificações, 2FA)

```html
<div class="toggle-wrapper">
  <input type="checkbox" id="enable-2fa" class="toggle-input" style="position:absolute; opacity:0"/>
  <label class="toggle-slider" for="enable-2fa"></label>
  <span class="toggle-label">Autenticação em dois fatores</span>
</div>
```

```javascript
// ❌ Input tem opacity:0 — invisível mas no DOM
//*[@id='enable-2fa']

// ✅ Clicar no slider visual
//label[@for='enable-2fa']
// ou
//label[contains(@class,'toggle-slider')]

// Verificar estado atual antes de clicar:
const isChecked = await page.locator('#enable-2fa').evaluate(el => el.checked);
if (!isChecked) {
  await page.locator('label[for="enable-2fa"]').click();
}

// Com Playwright:
await page.getByLabel('Autenticação em dois fatores').click();
```

---

#### Caso 4 — Select customizado (dropdown falso — DIV que imita SELECT)

```html
<!-- Banca Stato usa muito este padrão para seleção de moeda/conta -->
<div class="custom-select">
  <div class="select-selected">EUR</div>
  <div class="select-items" style="display:none">
    <div class="select-option" data-value="EUR">EUR</div>
    <div class="select-option" data-value="USD">USD</div>
    <div class="select-option" data-value="CHF">CHF</div>
  </div>
</div>
```

```javascript
// ❌ Não é um <select> real — page.selectOption() não funciona
// ❌ As opções estão hidden até clicar no trigger

// ✅ Passo 1 — abrir o dropdown clicando no trigger
//div[contains(@class,'custom-select')]//div[contains(@class,'select-selected')]

// ✅ Passo 2 — clicar na opção desejada (agora visível)
//div[contains(@class,'select-option') and normalize-space(text())='USD']

// Com Playwright — sequência completa:
await page.locator('.select-selected').click();  // abre
await page.locator('.select-option').filter({ hasText: 'USD' }).click();  // seleciona
```

---

#### Como o scanner deve detectar estes padrões — regra geral:

```javascript
const isHiddenInput = (el) => {
  const style = window.getComputedStyle(el);
  return (
    style.display === 'none' ||
    style.visibility === 'hidden' ||
    parseFloat(style.opacity) === 0 ||
    el.type === 'hidden'
  );
};

const findRealClickTarget = (el) => {
  // Se o input está escondido, procura o label associado
  if (isHiddenInput(el) && el.id) {
    const label = document.querySelector(`label[for="${el.id}"]`);
    if (label) return label;
  }
  // Sobe na hierarquia à procura de label pai
  const parentLabel = el.closest('label');
  if (parentLabel) return parentLabel;

  // Fallback — retorna o próprio elemento
  return el;
};
```

---

### 6. OCR Integrado

O reconhecimento ótico de caracteres (OCR) está integrado e trabalha em conjunto com os outros algoritmos de identificação. Pode ser ativado como **último recurso** nos casos em que nenhuma outra estratégia de localização funciona.

---

## Problemas a Resolver

### 1. Sincronização entre HOVER PICK e PAGE SCANNER

Quando o engine é executado, o Page Scanner é disparado em cada elemento da página. O HOVER PICK — que captura elementos manualmente via XPath — ainda **não está sincronizado** com este ciclo.

É necessário garantir que os elementos capturados manualmente sejam **registados antes da execução do scanner**, evitando conflitos ou duplicações.

---

### 2. Refinamento da Lógica de Nomes Repetidos

A solução atual de sufixação (`_1`, `_2`) resolve o problema imediato, mas precisa de ser refinada para garantir consistência em cenários mais complexos:

- Quando a **ordem dos elementos muda** dinamicamente
- Quando **novos elementos são adicionados** à página entre execuções
- Quando o mesmo nome aparece em **contextos de página diferentes**

---

### 3. Migração para Playwright como Scanner Principal

Esta é a **mudança mais significativa** prevista no roadmap.

O objetivo é substituir a injeção de JavaScript no browser pelo **Playwright como motor principal de scanning**. O Playwright oferece:

- Acesso à **árvore de acessibilidade** (accessibility tree)
- Localização por **role, texto e hierarquia DOM**
- Detecção robusta de elementos **sem atributos bem definidos**
- Sincronização nativa com **estados dinâmicos da página**

Esta mudança é prioritária e será testada inicialmente nos casos onde o scanner atual falha — especialmente botões sem atributos e tabs dinâmicas.

---

## Próximos Passos

| Prioridade | Tarefa |
|---|---|
| 🔴 Alta | Sincronizar HOVER PICK com o Page Scanner |
| 🔴 Alta | Iniciar testes de integração com Playwright |
| 🟡 Média | Refinar comportamento final dos nomes repetidos |
| 🟢 Baixa | Expandir uso do OCR como fallback integrado |

---

*Documento gerado para uso interno — actualizar conforme evolução do projecto.*
