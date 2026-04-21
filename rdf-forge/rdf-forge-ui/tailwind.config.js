// TODO(audit-2026-04-21 P3): Tailwind added but @tailwind directives missing from styles.scss.
// Either integrate Tailwind (add directives + import in styles.scss) OR remove dependency.
// Currently dead config bloating install.
/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{html,ts}",
  ],
  theme: {
    extend: {},
  },
  plugins: [],
};
