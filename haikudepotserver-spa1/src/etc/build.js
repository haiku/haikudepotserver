/*
 * Copyright 2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/*
 The front-end part of this application in this module dates to tech which
 is now deprecated and the technology of it's time was to simply concat the
 source files together and minify them.  The modern build tools do
 "too much" and any simple tools are no longer updated.  So this script
 performs the simple task of concat + minifying the sources.

 A further task is to take the original source and copy it to the target.
 At the same time it will assemble a catalog of the sources in the
 intended order.
 */

import {
  readFileSync,
  readdirSync,
  appendFileSync,
  existsSync,
  mkdirSync,
  statSync,
  copyFileSync,
  unlinkSync
} from "node:fs";
import {join, basename, dirname, extname, parse} from "node:path";
import {minify} from "uglify-js"

function ensureTargetParentExists(targetObject) {
  const targetParent = dirname(targetObject);

  if (!existsSync(targetParent)) {
    mkdirSync(targetParent, {recursive: true});
  }
}

function ensureObjectNotExists(targetObject) {
  if (existsSync(targetObject)) {
    unlinkSync(targetObject);
  }
}

function readdirFullPathsAndFilterSync(dir, pattern) {
  return readdirSync(dir)
    .filter(f => !f.startsWith('.'))
    .filter(f => !pattern || pattern.test(f))
    .map(f => join(dir, f));
}

function concatenateFilesSync(targetFile, sourceFiles) {

  function minifyObject(extension, data) {
    switch (extension.toLowerCase()) {
      case '.js':
        return minify(data,
          {
            compress: {
              dead_code: true
            }
          }).code;
      case '.css':
      default:
        return data;
    }
  }

  console.info(`will append ${sourceFiles.length} files to [${targetFile}]`)

  ensureObjectNotExists(targetFile);
  ensureTargetParentExists(targetFile);

  sourceFiles
    .map(file => {
      return {
        filename: file,
        data: minifyObject(extname(file), readFileSync(file, {encoding: 'utf8'}))
      }
    })
    .map(fileAndData => fileAndData) // TODO obfuscate
    .forEach(fileAndData => {
      appendFileSync(targetFile, fileAndData.data, {encoding: 'utf8'});
      appendFileSync(targetFile, '\n\n');
      const fileBasename = basename(fileAndData.filename);
    });
}

function copyAllFilesSync(targetRoot, sourceRoot) {
  console.info(`will copy from [${sourceRoot}] --> [${targetRoot}]`);

  if (!existsSync(targetRoot)) {
    mkdirSync(targetRoot, {recursive: true});
  }

  function recursiveCopyInternal(targetRootSub, sourceRootSub) {
    readdirSync(sourceRootSub)
      .filter(f => !f.startsWith("."))
      .forEach(f => {
        const sourceObject = join(sourceRootSub, f);
        const targetObject = join(targetRootSub, f);
        const stat = statSync(sourceObject);

        if (stat.isDirectory()) {
          if (!existsSync(targetObject)) {
            mkdirSync(join(targetRootSub, f), {recursive: true});
          }
          recursiveCopyInternal(targetObject, sourceObject);
        } else if (stat.isFile()) {
          ensureObjectNotExists(targetObject)
          copyFileSync(sourceObject, targetObject);
        }
      });
  }

  recursiveCopyInternal(targetRoot, sourceRoot);
}

function createIndex(targetFile, indexBase, sourceBase, sourceFiles) {

  function translateSourceFileForIndex(file) {

    function translatePathToClasspathPath(file) {
      if (!file || file === '/') {
        return '';
      }

      const parsed = parse(file);
      return translatePathToClasspathPath(parsed.dir) + '/' + parsed.base;
    }

    if (!file.startsWith(sourceBase)) {
      throw Error(`expected the source file [${file}] to start with [${sourceBase}]`);
    }

    return translatePathToClasspathPath(file.substring(sourceBase.length));
  }

  ensureObjectNotExists(targetFile);

  sourceFiles
    .map(sourceFile => indexBase + translateSourceFileForIndex(sourceFile))
    .forEach(classpathSourcePath => {
      appendFileSync(targetFile, classpathSourcePath + '\n', {encoding: 'utf8'});
    });
}

const JS_ROOT_SRC = join('src', 'main', 'javascript');
const CSS_ROOT_SRC = join('src', 'main', 'css');
const IMG_ROOT_SRC = join('src', 'main', 'img');
const CLASSES_TARGET = join('target', 'generated-resources', 'spa1');

const SRC_FILES_JS_APP = [
  [
    join(JS_ROOT_SRC, 'env.js'),
    join(JS_ROOT_SRC, 'app', 'haikudepotserver.js'),
    join(JS_ROOT_SRC, 'app', 'misc.js'),
    join(JS_ROOT_SRC, 'app', 'routes.js'),
    join(JS_ROOT_SRC, 'app', 'constants.js')
  ],
  readdirFullPathsAndFilterSync(join(JS_ROOT_SRC, 'app', 'directive'), /^.+\.js$/),
  readdirFullPathsAndFilterSync(join(JS_ROOT_SRC, 'app', 'controller'), /^.+\.js$/),
  readdirFullPathsAndFilterSync(join(JS_ROOT_SRC, 'app', 'service'), /^.+\.js$/),
  readdirFullPathsAndFilterSync(join(JS_ROOT_SRC, 'app', 'filter'), /^.+\.js$/)
].flat();

const SRC_FILES_JS_VENDOR = [
  join("node_modules", "moment", "moment.js"),
  join("node_modules", "underscore", "underscore.js"),
  join("node_modules", "angular", "angular.js"),
  join("node_modules", "angular-route", "angular-route.js"),
  join("node_modules", "npm-modernizr", "modernizr.js"),
  join("node_modules", "lru-cache", "lib", "lru-cache.js")
];

const SRC_FILES_CSS = [
  await readdirFullPathsAndFilterSync(CSS_ROOT_SRC + '/singlepage', /^.+\.css$/),
  await readdirFullPathsAndFilterSync(CSS_ROOT_SRC + '/multipage', /^.+\.css$/)
].flat();

console.info("building concatenated + minified targets");

concatenateFilesSync(join(CLASSES_TARGET, 'js', 'app.concat.min.js'), SRC_FILES_JS_APP);
concatenateFilesSync(join(CLASSES_TARGET, 'js', 'vendor.concat.min.js'), SRC_FILES_JS_VENDOR);
concatenateFilesSync(join(CLASSES_TARGET, 'css', 'app.concat.min.css'), SRC_FILES_CSS);

console.info("copying raw source files");

copyAllFilesSync(join(CLASSES_TARGET, 'js'), join(JS_ROOT_SRC));
copyAllFilesSync(join(CLASSES_TARGET, 'css'), join(CSS_ROOT_SRC));
copyAllFilesSync(join(CLASSES_TARGET, 'img'), join(IMG_ROOT_SRC));

console.info("creating indexes");

createIndex(join(CLASSES_TARGET, 'js', 'index.txt'), '', JS_ROOT_SRC, SRC_FILES_JS_APP);
createIndex(join(CLASSES_TARGET, 'css', 'index.txt'), '', CSS_ROOT_SRC, SRC_FILES_CSS);
