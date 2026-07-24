import org.customerxp.cd.CdJobParameterScripts

/**
 * Registers CD pipeline {@code properties(parameters([...]))} — single source for {@code cd-pipeline.jenkinsfile}.
 *
 * @param args.pipelineEnv or args.env — Jenkins binding env (pass {@code env} from {@code script \{ \}})
 */
def call(Map args = [:]) {
    def pipelineEnv = args.pipelineEnv != null ? args.pipelineEnv : args.env

    def ui = cdPipelineJobUiConfig(pipelineEnv)
    def regUi = ui.registry
    def moduleSelectedScript = ui.moduleSelectedScript
    def perDbJsonForScripts = ui.perDbJsonForScripts
    def jsMapFromRegistry = ui.jsMapFromRegistry
    def jdkChoiceValues = ui.jdkChoiceValues
    def jdkChoiceDefault = ui.jdkChoiceDefault
    def ccSetupModuleIdUi = ui.ccSetupModuleIdUi
    def ccPortMidEscJs = ui.ccPortMidEscJs

    properties([
        parameters([
            [$class: 'CascadeChoiceParameter',
                name   : 'MODULE_SELECTED',
                choiceType: 'PT_CHECKBOX',
                description: 'Select installer module(s) to deploy',
                script : [
                    $class: 'GroovyScript',
                    script: [
                        $class: 'SecureGroovyScript',
                        sandbox: true,
                        script: "${moduleSelectedScript}"
                    ]
                ]
            ],
            [$class: 'DynamicReferenceParameter',
                name   : 'MODULE_VERSION_CHOICES',
                referencedParameters: 'MODULE_SELECTED',
                choiceType: 'ET_FORMATTED_HTML',
                description: 'GitLab tag per selected module; check a row to enter a manual Nexus snapshot instead.',
                script : [
                    $class: 'GroovyScript',
                    script: [
                        $class: 'SecureGroovyScript',
                        sandbox: false,
                        script: ui.moduleVersionChoicesScript
                    ]
                ]
            ],
            [$class: 'ExtendedChoiceParameterDefinition',
                name         : 'JDK_VERSION',
                type         : 'PT_SINGLE_SELECT',
                value        : "${jdkChoiceValues}",
                defaultValue : "${jdkChoiceDefault}",
                description  : 'JDK on the app server (override via CX_JDK_VERSION_OPTIONS / CX_JDK_VERSION_DEFAULT).'
            ],
            string(name: 'DEP_BASE_PATH', defaultValue: '', description: 'Deploy root on app server; for SHARED_DB modules must match dep_base_path in cc_db_context.json.'),
            string(name: 'CC_PORT', defaultValue: '', description: "cc HTTP port — enabled only when **${ccSetupModuleIdUi ?: 'cc'}** is checked; greyed out otherwise."),
            [$class: 'CascadeChoiceParameter',
                name   : 'APP_SERVER',
                referencedParameters: 'MODULE_SELECTED',
                choiceType: 'PT_SINGLE_SELECT',
                description: 'App server for this deploy (from JENKINS_CD_SERVERS_CONFIG_FILE).',
                script : [
                    $class: 'GroovyScript',
                    script: [
                        $class: 'SecureGroovyScript',
                        sandbox: false,
                        script: CdJobParameterScripts.appServerCascadeFromServersJson()
                    ]
                ]
            ],
            [$class: 'DynamicReferenceParameter',
                name   : 'PER_MODULE_DB_JSON',
                referencedParameters: 'MODULE_SELECTED,JDK_VERSION',
                choiceType: 'ET_FORMATTED_HTML',
                omitValueField: true,
                description: 'Per-module DB table (UX only) — Generate JSON, then paste into PER_MODULE_DB_JSON_SUBMITTED.',
                script : [
                    $class: 'GroovyScript',
                    script: [
                        $class: 'SecureGroovyScript',
                        sandbox: false,
                        script: CdJobParameterScripts.perModuleDbJsonFormattedHtml(perDbJsonForScripts)
                    ],
                    fallbackScript: [
                        $class: 'SecureGroovyScript',
                        sandbox: false,
                        script: CdJobParameterScripts.perModuleDbJsonFallbackGroovy()
                    ]
                ]
            ],
            [$class: 'TextParameterDefinition',
                name        : 'PER_MODULE_DB_JSON_SUBMITTED',
                defaultValue: '',
                description : 'Required for PRIMARY_DB/CUSTOM_DB modules — paste JSON from PER_MODULE_DB_JSON (Copy button).'
            ]
        ] + [
            [$class: 'CascadeChoiceParameter',
                name   : 'DB_SERVER',
                referencedParameters: 'MODULE_SELECTED',
                choiceType: 'PT_SINGLE_SELECT',
                description: 'Global DB setup name (mixed deploys; hidden for shared-only or per-module-only).',
                script : [
                    $class: 'GroovyScript',
                    script: [
                        $class: 'SecureGroovyScript',
                        sandbox: false,
                        script: CdJobParameterScripts.globalDbServerCascade(perDbJsonForScripts)
                    ]
                ]
            ],
            [$class: 'DynamicReferenceParameter',
                name   : 'DB_CREDENTIALS_INPUT',
                referencedParameters: 'MODULE_SELECTED,DB_SERVER',
                choiceType: 'ET_FORMATTED_HTML',
                description: 'Global DB user/password (when shown; use per-module table otherwise).',
                script : [
                    $class: 'GroovyScript',
                    script: [
                        $class: 'SecureGroovyScript',
                        sandbox: false,
                        script: CdJobParameterScripts.globalDbCredentialsFormattedHtml(jsMapFromRegistry, ccPortMidEscJs, perDbJsonForScripts)
                    ]
                ]
            ]
        ]
        )
    ])

    echo "CD module registry (parameters UI): version=${regUi?.version} gitRef=${regUi?.gitRef ?: ''} modules=${regUi?.modules?.size()} GitLab API=${ui.gitlabApiUrlResolved ?: '—'} GitLab token credential id=${ui.gitlabTokenCredentialIdResolved ?: '—'}"
}
