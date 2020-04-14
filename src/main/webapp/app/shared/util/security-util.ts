export const safeUnescape = (s: string, mode = 'text/html') => {
    const parser = new DOMParser();
    return parser.parseFromString(s, mode).body.textContent;
};
