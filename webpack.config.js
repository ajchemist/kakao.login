const path = require("path");
const HtmlWebpackPlugin = require('html-webpack-plugin');
const HtmlWebpackInjector = require('html-webpack-injector');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const { WebpackManifestPlugin } = require('webpack-manifest-plugin');
const { CleanWebpackPlugin } = require('clean-webpack-plugin');
const webpack = require("webpack");
const devMode = process.env.NODE_ENV !== 'production';


const plugins = [
  new HtmlWebpackPlugin({
    template: 'src/web/index.html',
    filename: 'index.html',
    chunks: ["js/index", "css/core_head"],
    chunksConfig: {
      async: ["css/core_head"],
      defer: ["js/index"]
    }
  }),
  new HtmlWebpackInjector(),
  new CleanWebpackPlugin(),
  new MiniCssExtractPlugin({
    filename: devMode ? "[name].dev.css" : "[name].[chunkhash].css",
    chunkFilename: devMode ? "[id].dev.css" : "[id].[chunkhash].css" // [contenthash]
  }),
  new webpack.SourceMapDevToolPlugin({
    test: /\.css$/i,
    filename: "[file].map",
    noSources: true,
    module: false,
    columns: false,
  }),
];
if (devMode)
{
  plugins.push(new webpack.HotModuleReplacementPlugin());
}


module.exports = {
  mode: process.env.NODE_ENV || 'development',
  entry: {
    "js/index": "./src/web/index.js",
    "css/core_head": "./src/web/core.css"
  },
  output: {
    // output directory will be clean before webpack build
    path: path.resolve(__dirname, './dist'),
    filename: devMode ? "[name]_bundle.dev.js": "[name]_bundle.[chunkhash].js",
    sourceMapFilename: '[file].map',
  },
  devtool: false,
  plugins,
  optimization: {
    // runtimeChunk: {
    //   name: "runtime"
    // },
    // splitChunks: {
    //   name: "vendor",
    //   chunks: "initial"
    // }
  },
  module: {
    rules: [
      {
	test: /\.(png|jpe?g|gif|svg|webp)$/i,
	use: [
	  {
	    loader: 'file-loader',
	    options: {
	      name: 'img/[name].[ext]'
	    }
	  }
	]
      },
      {
	test: /\.css$/i,
	include: path.resolve(__dirname, 'src'),
	exclude: /node_modules/,
	use: [
	  MiniCssExtractPlugin.loader, // DO NOT USE style-loader
	  {
	    loader: "css-loader", // CSS -> CommonJS
	    options: {
	      url: false,
	      sourceMap: false,
	      importLoaders: 1,
	      // config: {
	      //   path: path.resolve(__dirname),
	      // }
	    }
	  },
	  "postcss-loader"
	]
      }
    ]
  },
};
