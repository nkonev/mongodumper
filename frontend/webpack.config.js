const path = require("path");
const HtmlWebpackPlugin = require("html-webpack-plugin");
const CssExtractPlugin = require('mini-css-extract-plugin');
const {CleanWebpackPlugin} = require('clean-webpack-plugin');
const LiveReloadPlugin = require('webpack-livereload-plugin');
const CopyPlugin = require('copy-webpack-plugin');

const contentBase = path.join(__dirname, "../src/main/resources/static");

module.exports = (env, argv) => {
    const pluginsArray = [
        new CleanWebpackPlugin({
            watch: false,
            dangerouslyAllowCleanPatternsOutsideProject: true,
            verbose: true,
            dry: false
        }),
        new CopyPlugin([
            { from: './public', to: contentBase, ignore: ['index.html'] },
        ]),
        new HtmlWebpackPlugin({
            template: "./public/index.html"
        }),
        new CssExtractPlugin({
            // Options similar to the same options in webpackOptions.output
            // all options are optional
            filename: '[name].css',
            chunkFilename: '[id].css',
            ignoreOrder: false, // Enable to remove warnings about conflicting order
        }),
    ];
    if (argv.mode === 'development') {
        console.log("Starting LiveReloadPlugin");
        pluginsArray.push(
            new LiveReloadPlugin({
                appendScriptTag: true,
                port: 35735
            })
        );
    }

    return {
        entry: "./src/index.js",
        output: {
            path: contentBase,
            filename: "index-bundle.js"
        },
        module: {
            rules: [
                {
                    test: /\.js$/,
                    exclude: /node_modules/,
                    use: {
                        loader: "babel-loader"
                    },
                },
                {
                    test: /\.css$/,
                    use: [
                        {
                            loader: CssExtractPlugin.loader,
                            options: {
                                hot: process.env.NODE_ENV === 'development',
                            },
                        },
                        'css-loader',
                    ]
                },
                {
                    test: /\.(svg)$/,
                    exclude: /fonts/, /* dont want svg fonts from fonts folder to be included */
                    use: [
                        {
                            loader: 'svg-url-loader',
                            options: {
                                noquotes: true,
                            },
                        },
                    ],
                },
                {
                    test: /\.(ttf|eot|woff|woff2)$/,
                    use: [
                        {
                            loader: 'url-loader',
                            options: {
                                name: '[path][name].[ext]',
                                limit: '4096'
                            }
                        }
                    ],
                },
            ]
        },
        plugins: pluginsArray,
    }
};