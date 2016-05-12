{
  'targets' : [
    {
      'target_name': 'swe_test_apk',
        'type': 'none',
        'dependencies': [
          'swe_shell_app_apk_java',
          '../base/base.gyp:base_java_test_support',
          '../content/content_shell_and_tests.gyp:content_java_test_support',
          '../net/net.gyp:net_java_test_support',
        ],
        'variables': {
          'apk_name': 'SWETest',
          'java_in_dir': './test',
          'is_test_apk': 1,
        },
        'includes': [ '../../../build/java_apk.gypi' ],
    },
  ],
}

