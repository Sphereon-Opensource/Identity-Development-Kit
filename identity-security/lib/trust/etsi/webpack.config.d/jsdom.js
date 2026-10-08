// Provide jsdom for Node.js tests to support DOM operations required by xmlutil
config.externals = config.externals || {};
config.externals.jsdom = 'commonjs jsdom';
