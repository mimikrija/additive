const croatianPlurals = new Intl.PluralRules('hr');

const messages = {
    en: {
        language: 'Language',
        level: 'Level',
        currentStreak: 'Streak',
        bestStreak: 'Best streak',
        icecreams: 'Ice creams',
        noCount: 'No count yet',
        countUnavailable: 'Count unavailable',
        icecreamCount: (count) => `${count} ${count === 1 ? 'ice cream' : 'ice creams'}`,
        ready: 'Ready to play?',
        chooseAnswer: 'Pick an answer',
        start: 'Start game',
        skip: 'Skip problem',
        loading: 'Loading a problem…',
        problemUnavailable: 'Could not load a problem.',
        loadFailed: 'Could not load the game. Try again.',
        tryAgain: 'Try again',
        resumeFailed: 'Could not resume your saved game.',
        next: 'Next problem',
        closed: 'This problem is already closed.',
        correct: 'Correct!',
        rewarded: 'Correct! You earned an ice cream.',
        wrong: 'Not quite. Try another answer or skip.',
        reload: 'Reload problem',
        invalidAnswer: 'Could not use that answer. Reload the problem and try again.',
        retryAnswer: (answer) => `Could not check your answer. Tap ${answer} to retry.`,
        skipFailed: 'Could not skip this problem. Try again.',
    },
    hr: {
        language: 'Jezik',
        level: 'Razina',
        currentStreak: 'Trenutni niz',
        bestStreak: 'Najbolji niz',
        icecreams: 'Sladoledi',
        noCount: 'Broj još nije dostupan',
        countUnavailable: 'Broj nije dostupan',
        icecreamCount: (count) => `${count} ${croatianPlurals.select(count) === 'one' ? 'sladoled' : 'sladoleda'}`,
        ready: 'Krećemo?',
        chooseAnswer: 'Odaberi odgovor',
        start: 'Započni igru',
        skip: 'Preskoči zadatak',
        loading: 'Učitavanje zadatka…',
        problemUnavailable: 'Zadatak se nije učitao.',
        loadFailed: 'Igra se nije učitala. Pokušaj ponovno.',
        tryAgain: 'Pokušaj ponovno',
        resumeFailed: 'Nije moguće nastaviti spremljenu igru.',
        next: 'Sljedeći zadatak',
        closed: 'Ovaj zadatak više nije aktivan.',
        correct: 'Točno!',
        rewarded: 'Točno! Osvojen je sladoled.',
        wrong: 'Nije točno. Odaberi drugi odgovor ili preskoči zadatak.',
        reload: 'Ponovno učitaj zadatak',
        invalidAnswer: 'Taj se odgovor ne može prihvatiti. Ponovno učitaj zadatak pa pokušaj opet.',
        retryAnswer: (answer) => `Odgovor nije provjeren. Dodirni ${answer} za ponovni pokušaj.`,
        skipFailed: 'Zadatak se ne može preskočiti. Pokušaj ponovno.',
    },
    nl: {
        language: 'Taal',
        level: 'Niveau',
        currentStreak: 'Reeks',
        bestStreak: 'Beste reeks',
        icecreams: 'IJsjes',
        noCount: 'Aantal nog niet beschikbaar',
        countUnavailable: 'Aantal niet beschikbaar',
        icecreamCount: (count) => `${count} ${count === 1 ? 'ijsje' : 'ijsjes'}`,
        ready: 'Klaar om te spelen?',
        chooseAnswer: 'Kies een antwoord',
        start: 'Spel starten',
        skip: 'Som overslaan',
        loading: 'Som wordt geladen…',
        problemUnavailable: 'De som kon niet worden geladen.',
        loadFailed: 'Het spel kon niet worden geladen. Probeer het opnieuw.',
        tryAgain: 'Opnieuw proberen',
        resumeFailed: 'Je opgeslagen spel kon niet worden hervat.',
        next: 'Volgende som',
        closed: 'Deze som is niet meer actief.',
        correct: 'Goed!',
        rewarded: 'Goed! Je hebt een ijsje verdiend.',
        wrong: 'Niet helemaal. Kies een ander antwoord of sla de som over.',
        reload: 'Som opnieuw laden',
        invalidAnswer: 'Dat antwoord kon niet worden gebruikt. Laad de som opnieuw en probeer het nog eens.',
        retryAnswer: (answer) => `Je antwoord kon niet worden gecontroleerd. Tik op ${answer} om het opnieuw te proberen.`,
        skipFailed: 'De som kon niet worden overgeslagen. Probeer het opnieuw.',
    },
};

const languageKey = 'additive-game-language';
let language = 'en';

function renderText(element) {
    const message = messages[language][element.dataset.text];
    element.textContent = typeof message === 'function' ? message(Number(element.dataset.textValue)) : message;
}

export function setGameText(element, key, value) {
    if (value === undefined) {
        delete element.dataset.textValue;
    } else {
        element.dataset.textValue = value;
    }
    if (!key) {
        delete element.dataset.text;
        element.textContent = '';
        return;
    }
    element.dataset.text = key;
    renderText(element);
}

export function initializeGameLanguage() {
    const buttons = document.querySelectorAll('[data-language]');
    const storedLanguage = localStorage.getItem(languageKey);

    function selectLanguage(selected) {
        language = selected;
        document.documentElement.lang = selected;
        document.querySelectorAll('[data-text]').forEach(renderText);
        for (const button of buttons) {
            button.setAttribute('aria-pressed', String(button.dataset.language === selected));
        }
    }

    selectLanguage(Object.hasOwn(messages, storedLanguage) ? storedLanguage : 'en');
    for (const button of buttons) {
        button.addEventListener('click', () => {
            const selected = button.dataset.language;
            selectLanguage(selected);
            localStorage.setItem(languageKey, selected);
        });
    }
}
