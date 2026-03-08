module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma')
    ],
    client: {
      jasmine: {
        // you can add configuration options for Jasmine here
        // the possible options are listed at https://jasmine.github.io/api/edge/Configuration.html
        // for example, you can disable the random execution with `random: false`
        // or set a specific seed with `seed: 4321`
      },
      clearContext: false // leave Jasmine Spec Runner output visible in browser
    },
    jasmineHtmlReporter: {
      suppressAll: true // removes the duplicated traces
    },
    coverageReporter: {
      dir: require('path').join(__dirname, './coverage/rdf-forge-ui'),
      subdir: '.',
      reporters: [
        { type: 'html' },
        { type: 'text-summary' },
        { type: 'lcovonly' }
      ],
      check: {
        global: {
          statements: 70,
          branches: 70,
          functions: 70,
          lines: 70
        },
        each: {
          statements: 60,
          branches: 60,
          functions: 60,
          lines: 60
        }
      },
      // Istanbul watermarks for coverage visualization
      watermarks: {
        statements: [70, 90],
        functions: [70, 90],
        branches: [70, 90],
        lines: [70, 90]
      }
    },
    reporters: ['progress', 'coverage'],
    port: 9876,
    colors: true,
    logLevel: config.LOG_INFO,
    autoWatch: true,
    browsers: ['ChromeHeadless'],
    customLaunchers: {
      ChromeHeadlessCI: {
        base: 'ChromeHeadless',
        flags: ['--no-sandbox', '--disable-gpu', '--disable-web-security']
      },
      ChromeDebug: {
        base: 'Chrome',
        flags: ['--remote-debugging-port=9333']
      }
    },
    singleRun: false,
    restartOnFileChange: true,
    // Increase browser no activity timeout for CI environments
    browserNoActivityTimeout: 60000,
    // Capture timeout for browsers
    captureTimeout: 60000,
    // Retry limit for failed tests
    retryLimit: 2
  });
};
