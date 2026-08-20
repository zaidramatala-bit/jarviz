//--------------------------------------------------------------------------
// Copyright 2020 Expedia, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//--------------------------------------------------------------------------

const ESLintPlugin = require('eslint-webpack-plugin');
const HtmlInlineScriptPlugin = require('html-inline-script-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const path = require('path');
const TerserPlugin = require('terser-webpack-plugin');

const pkg = require('./package.json');
const {processData} = require('./lib/index');

const devMode = process.env.NODE_ENV !== 'production';

module.exports = {
    devtool: devMode ? 'source-map' : false,
    mode: devMode ? 'development' : 'production',
    entry: path.join(__dirname, 'lib/client/index.js'),
    output: {
        path: path.join(__dirname, 'build/client'),
        filename: `jarviz-client.js`
    },
    optimization: {
        // Keep dependency license banners inside the bundle: the production output is a single
        // self-contained HTML file, so a separate LICENSE.txt asset would be lost.
        minimizer: [new TerserPlugin({extractComments: false})]
    },
    module: {
        rules: [
            {
                test: /\.js$/,
                loader: 'babel-loader',
                exclude: /node_modules/
            },
            {
                test: /\.css$/,
                use: ['style-loader', 'css-loader']
            },
            {
                test: /\.(png|svg)$/,
                type: 'asset/inline'
            },
            {
                test: /\.(eot|ttf)$/,
                type: 'asset/inline'
            },
            {
                test: /\.jpg$/,
                type: 'asset/resource'
            }
        ]
    },
    plugins: [
        new ESLintPlugin({
            emitWarning: true,
            quiet: true,
            exclude: ['node_modules', 'tests']
        }),
        new HtmlWebpackPlugin({
            title: `${pkg.name} - ${pkg.description}`,
            filename: devMode ? 'index.html' : 'jarviz-graph.html',
            template: path.join(__dirname, 'lib/client/index.html'),
            templateParameters: {
                jarvizData: devMode ? 'false' : '{{{JARVIZ_DATA}}}'
            }
        }),
        ...(devMode ? [] : [new HtmlInlineScriptPlugin()])
    ],
    devServer: {
        static: {
            directory: path.join(__dirname, 'lib/client')
        },
        port: 8080,
        open: true,
        setupMiddlewares: function(middlewares, devServer) {
            devServer.app.get('/data', function(req, res) {
                const fileName = req.query.name || 'jarviz-graph-data-1';
                console.log(`Requesting "${fileName}"`);
                processData(path.join(__dirname, `lib/mock/${fileName}.jsonl`), ({data, dataName}) => {
                    console.log('Processed', dataName);
                    res.json({data});
                });
            });
            return middlewares;
        }
    }
};
