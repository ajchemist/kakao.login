const postcssPresetEnv = require('postcss-preset-env');
const tailwindcss = require('tailwindcss');
const purgecss = require('@fullhuman/postcss-purgecss');
const cssnano = require('cssnano');


module.exports = {
  plugins: [
    postcssPresetEnv,
    require('postcss-import')({
      plugins: [],
      path: ['./node_modules']
    }),
    tailwindcss('./tailwind.config.js'),
    process.env.NODE_ENV === 'production' ? require('autoprefixer') : null,
    process.env.NODE_ENV === 'production' ? cssnano({ preset: 'default' }) : null,
  ]
};
