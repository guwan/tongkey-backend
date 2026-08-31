#!/bin/bash

# ============================================================
#  统一部署脚本 deploy.sh
#  用法:
#    ./deploy.sh                        # 交互菜单模式
#    ./deploy.sh init                   # 初始化新项目配置
#    ./deploy.sh <项目名> <动作>         # 命令行快捷部署
#      动作: deploy | start | stop | restart | status
# ============================================================

# ─── 全局配置 ───────────────────────────────────────────────
DEPLOY_CONFIG_DIR="${HOME}/.deploy/configs"
GIT_BASE="https://github.com/guwan"
DEFAULT_BRANCH="main"
DEFAULT_BACKEND_CODE_ROOT="/data/codes/backend"
DEFAULT_FRONTEND_CODE_ROOT="/data/codes/frontend"
DEFAULT_APP_ROOT="/app"
DEFAULT_LOG_ROOT="/var/backend/logs"

# ─── 颜色 ───────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BLUE='\033[0;34m'
BOLD='\033[1m'
PLAIN='\033[0m'

print_info()  { echo -e "${GREEN}[INFO]${PLAIN} $1"; }
print_error() { echo -e "${RED}[ERROR]${PLAIN} $1"; }
print_warn()  { echo -e "${YELLOW}[WARN]${PLAIN} $1"; }
print_title() { echo -e "${BOLD}${CYAN}$1${PLAIN}"; }
print_step()  { echo -e "${BLUE}  ▶${PLAIN} $1"; }

# ─── 工具函数 ────────────────────────────────────────────────

# read_input VARNAME "提示文字" "默认值"
# 将用户输入直接写入 VARNAME，不用命令替换
read_input() {
    local _var="$1"
    local _prompt="$2"
    local _default="$3"
    local _result
    if [ -n "$_default" ]; then
        echo -ne "${CYAN}  ${_prompt}${PLAIN} [${YELLOW}${_default}${PLAIN}]: "
    else
        echo -ne "${CYAN}  ${_prompt}${PLAIN}: "
    fi
    read -r _result
    printf -v "$_var" '%s' "${_result:-$_default}"
}

# select_from_list VARNAME "标题" item1 item2 ...
# 将选中项直接写入 VARNAME，不用命令替换
select_from_list() {
    local _var="$1"
    local _title="$2"
    shift 2
    local _items=("$@")
    local _choice _selected

    if [ ${#_items[@]} -eq 0 ]; then
        printf -v "$_var" '%s' ""
        return
    fi

    echo -e "${CYAN}  ${_title}${PLAIN}"
    for i in "${!_items[@]}"; do
        echo -e "    ${YELLOW}$((i+1))${PLAIN}) ${_items[$i]}"
    done
    echo -ne "  ${CYAN}请输入编号${PLAIN} [1]: "
    read -r _choice
    _choice="${_choice:-1}"

    if [[ "$_choice" =~ ^[0-9]+$ ]] && [ "$_choice" -ge 1 ] && [ "$_choice" -le ${#_items[@]} ]; then
        _selected="${_items[$((_choice-1))]}"
    else
        _selected="${_items[0]}"
    fi
    printf -v "$_var" '%s' "$_selected"
}

# 从完整 git url 或短名称提取应用名
extract_app_name() {
    local input="$1"
    input="${input%/}"
    input="${input%.git}"
    echo "${input##*/}"
}

# 补全 git 地址：短名 → 完整 URL
complete_git_url() {
    local input="$1"
    input="${input%/}"
    if [[ "$input" == http* ]] || [[ "$input" == git@* ]]; then
        echo "$input"
    else
        echo "${GIT_BASE}/${input}.git"
    fi
}

# 扫描系统可用 JDK，逐行输出路径
scan_jdks() {
    local search_dirs=(
        "/usr/lib/jvm"
        "/usr/local/jvm"
        "/opt"
        "${HOME}/.sdkman/candidates/java"
        "${HOME}/jdk"
        "/usr/java"
    )
    declare -A _seen
    for dir in "${search_dirs[@]}"; do
        [ -d "$dir" ] || continue
        while IFS= read -r java_bin; do
            local jdk_home
            jdk_home="$(dirname "$(dirname "$java_bin")")"
            if [ -z "${_seen[$jdk_home]:-}" ]; then
                _seen[$jdk_home]=1
                echo "$jdk_home"
            fi
        done < <(find "$dir" -maxdepth 4 -name "java" -path "*/bin/java" -type f 2>/dev/null)
    done
}

# 扫描系统可用 Node，逐行输出路径
scan_nodes() {
    declare -A _seen
    if [ -d "${HOME}/.nvm/versions/node" ]; then
        while IFS= read -r node_bin; do
            local node_home
            node_home="$(dirname "$(dirname "$node_bin")")"
            if [ -z "${_seen[$node_home]:-}" ]; then
                _seen[$node_home]=1
                echo "$node_home"
            fi
        done < <(find "${HOME}/.nvm/versions/node" -maxdepth 3 -name "node" -path "*/bin/node" 2>/dev/null)
    fi
    for dir in /usr/local /usr /opt/node; do
        if [ -x "$dir/bin/node" ] && [ -z "${_seen[$dir]:-}" ]; then
            _seen[$dir]=1
            echo "$dir"
        fi
    done
}

# ─── 目录创建（支持 sudo）────────────────────────────────────

# 创建目录，无权限时自动尝试 sudo
make_dir_safe() {
    local dir="$1"
    [ -d "$dir" ] && return 0
    if mkdir -p "$dir" 2>/dev/null; then
        return 0
    fi
    print_warn "无权限创建目录 $dir，尝试 sudo..."
    if sudo mkdir -p "$dir" && sudo chown "$(whoami)" "$dir"; then
        print_info "已创建: $dir"
        return 0
    else
        print_error "无法创建目录: $dir"
        print_error "请手动执行: sudo mkdir -p $dir && sudo chown $(whoami) $dir"
        return 1
    fi
}

# ─── 配置文件操作 ─────────────────────────────────────────────

mkdir -p "$DEPLOY_CONFIG_DIR"

load_config() {
    local project="$1"
    local conf="${DEPLOY_CONFIG_DIR}/${project}.conf"
    if [ ! -f "$conf" ]; then
        print_error "找不到项目 [${project}] 的配置文件: $conf"
        print_info "请先运行: ./deploy.sh init"
        exit 1
    fi
    source "$conf"
    GIT_REPO="${GIT_REPO%/}"
}

save_config() {
    local project="$1"
    local conf="${DEPLOY_CONFIG_DIR}/${project}.conf"
    GIT_REPO="${GIT_REPO%/}"
    cat > "$conf" << EOF
# 项目配置 - 由 deploy.sh 自动生成
# 生成时间: $(date '+%Y-%m-%d %H:%M:%S')
PROJECT_TYPE="${PROJECT_TYPE}"
APP_PURE_NAME="${APP_PURE_NAME}"
GIT_REPO="${GIT_REPO}"
GIT_BRANCH="${GIT_BRANCH}"
APP_HOME="${APP_HOME}"
CODE_PATH_PARENT="${CODE_PATH_PARENT}"
CODE_PATH="${CODE_PATH}"
EOF
    if [ "$PROJECT_TYPE" = "backend" ]; then
        cat >> "$conf" << EOF
LOG_PATH="${LOG_PATH}"
LOG_FILE="${LOG_FILE}"
APP_JAVA_HOME="${APP_JAVA_HOME}"
SPRING_PROFILE="${SPRING_PROFILE}"
# 外部配置文件（服务器上手动放置的 application-*.yml，不进 git）
# Spring Boot 会在内部 application-*.yml 加载后，额外加载此文件并合并覆盖
# 例: /app/tongkey-backend/application-secret.yml （含真实 DB 密码、加密密钥等）
APP_CONFIG_FILE="${APP_CONFIG_FILE}"
# JVM 内存参数
JVM_XMS="${JVM_XMS}"
JVM_XMX="${JVM_XMX}"
JVM_EXTRA_OPTS="${JVM_EXTRA_OPTS}"
EOF
    else
        cat >> "$conf" << EOF
NODE_HOME="${NODE_HOME}"
PKG_MANAGER="${PKG_MANAGER}"
DIST_DIR="${DIST_DIR}"
EOF
    fi
    chmod 600 "$conf"
    print_info "配置已保存到: $conf"
}

list_projects() {
    for f in "${DEPLOY_CONFIG_DIR}"/*.conf; do
        [ -f "$f" ] && basename "$f" .conf
    done
}

# ─── 初始化配置 ───────────────────────────────────────────────

cmd_init() {
    echo ""
    print_title "═══════════════════════════════════════"
    print_title "       初始化项目部署配置"
    print_title "═══════════════════════════════════════"

    # 选择项目类型
    echo -e "\n${CYAN}  项目类型:${PLAIN}"
    echo -e "    ${YELLOW}1${PLAIN}) 后端 (Java/Spring Boot)"
    echo -e "    ${YELLOW}2${PLAIN}) 前端 (Node/Vue/React)"
    echo -ne "  ${CYAN}请选择${PLAIN} [1]: "
    local type_choice
    read -r type_choice
    if [ "${type_choice:-1}" = "2" ]; then
        PROJECT_TYPE="frontend"
    else
        PROJECT_TYPE="backend"
    fi

    echo ""

    # Git 地址（智能补全）
    echo -e "${CYAN}  Git 地址输入说明:${PLAIN}"
    echo -e "  可以只输入项目名，例如 ${YELLOW}teaching-backend${PLAIN}"
    echo -e "  将自动补全为 ${YELLOW}${GITEE_BASE}/teaching-backend.git${PLAIN}"

    local git_input
    read_input git_input "Git 仓库 (项目名或完整URL)" ""
    GIT_REPO=$(complete_git_url "$git_input")
    echo -e "  ${GREEN}✓ Git 地址: ${GIT_REPO}${PLAIN}"

    # 自动提取应用名
    local auto_name
    auto_name=$(extract_app_name "$GIT_REPO")
    read_input APP_PURE_NAME "应用名称" "$auto_name"

    # Git 分支
    read_input GIT_BRANCH "Git 分支" "$DEFAULT_BRANCH"

    echo ""

    if [ "$PROJECT_TYPE" = "backend" ]; then
        read_input CODE_PATH_PARENT "代码存放父目录" "$DEFAULT_BACKEND_CODE_ROOT"
        CODE_PATH="${CODE_PATH_PARENT}/${APP_PURE_NAME}"

        read_input APP_HOME "应用部署目录" "${DEFAULT_APP_ROOT}/${APP_PURE_NAME}"

        read_input LOG_PATH "日志目录" "$DEFAULT_LOG_ROOT"
        LOG_FILE="${LOG_PATH}/${APP_PURE_NAME}.log"

        read_input SPRING_PROFILE "Spring 运行环境" "dev,local"

        read_input APP_CONFIG_FILE "外部配置文件路径(可选，留空跳过)" ""
        read_input JVM_XMS "JVM 初始堆内存" "256m"
        read_input JVM_XMX "JVM 最大堆内存" "1024m"
        read_input JVM_EXTRA_OPTS "JVM 额外参数(可选)" "-XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError"

        echo ""
        print_step "扫描系统 JDK..."
        local jdk_list=()
        while IFS= read -r jdk; do
            [ -n "$jdk" ] && jdk_list+=("$jdk")
        done < <(scan_jdks)

        if [ ${#jdk_list[@]} -eq 0 ]; then
            print_warn "未找到可用 JDK，请手动输入 JAVA_HOME"
            read_input APP_JAVA_HOME "JAVA_HOME 路径" ""
        else
            local jdk_labels=()
            for j in "${jdk_list[@]}"; do
                local ver
                ver=$("${j}/bin/java" -version 2>&1 | head -1 | awk -F'"' '{print $2}')
                jdk_labels+=("${j}  (Java ${ver})")
            done
            local selected_label
            select_from_list selected_label "选择 JDK:" "${jdk_labels[@]}"
            APP_JAVA_HOME="${selected_label%%  (*}"
            echo -e "  ${GREEN}✓ 已选择: ${APP_JAVA_HOME}${PLAIN}"
            read_input APP_JAVA_HOME "确认 JAVA_HOME (可修改)" "$APP_JAVA_HOME"
        fi

    else
        read_input CODE_PATH_PARENT "代码存放父目录" "$DEFAULT_FRONTEND_CODE_ROOT"
        CODE_PATH="${CODE_PATH_PARENT}/${APP_PURE_NAME}"

        read_input APP_HOME "应用部署目录 (Nginx root)" "${DEFAULT_APP_ROOT}/${APP_PURE_NAME}"

        echo ""
        print_step "扫描系统 Node..."
        local node_list=()
        while IFS= read -r node; do
            [ -n "$node" ] && node_list+=("$node")
        done < <(scan_nodes)

        if [ ${#node_list[@]} -eq 0 ]; then
            print_warn "未找到可用 Node，将使用系统默认 node"
            NODE_HOME=""
        else
            local node_labels=()
            for n in "${node_list[@]}"; do
                local ver
                ver=$("${n}/bin/node" --version 2>/dev/null || echo "?")
                node_labels+=("${n}  (Node ${ver})")
            done
            local selected_label
            select_from_list selected_label "选择 Node 版本:" "${node_labels[@]}"
            NODE_HOME="${selected_label%%  (*}"
            echo -e "  ${GREEN}✓ 已选择: ${NODE_HOME}${PLAIN}"
            read_input NODE_HOME "确认 Node 路径 (可修改)" "$NODE_HOME"
        fi

        echo -e "\n${CYAN}  包管理器:${PLAIN}"
        echo -e "    ${YELLOW}1${PLAIN}) pnpm  ${YELLOW}2${PLAIN}) npm  ${YELLOW}3${PLAIN}) yarn"
        echo -ne "  ${CYAN}请选择${PLAIN} [1]: "
        local pm_choice
        read -r pm_choice
        case "${pm_choice:-1}" in
            2) PKG_MANAGER="npm" ;;
            3) PKG_MANAGER="yarn" ;;
            *) PKG_MANAGER="pnpm" ;;
        esac

        read_input DIST_DIR "构建输出目录" "dist"
    fi

    echo ""
    print_title "─── 配置确认 ───────────────────────────"
    echo -e "  项目类型  : ${YELLOW}${PROJECT_TYPE}${PLAIN}"
    echo -e "  应用名称  : ${YELLOW}${APP_PURE_NAME}${PLAIN}"
    echo -e "  Git 仓库  : ${YELLOW}${GIT_REPO}${PLAIN}"
    echo -e "  Git 分支  : ${YELLOW}${GIT_BRANCH}${PLAIN}"
    echo -e "  代码目录  : ${YELLOW}${CODE_PATH}${PLAIN}"
    echo -e "  部署目录  : ${YELLOW}${APP_HOME}${PLAIN}"
    if [ "$PROJECT_TYPE" = "backend" ]; then
        echo -e "  日志文件  : ${YELLOW}${LOG_FILE}${PLAIN}"
        echo -e "  JAVA_HOME : ${YELLOW}${APP_JAVA_HOME}${PLAIN}"
        echo -e "  Spring环境: ${YELLOW}${SPRING_PROFILE}${PLAIN}"
        [ -n "${APP_CONFIG_FILE}" ] && echo -e "  外部配置  : ${YELLOW}${APP_CONFIG_FILE}${PLAIN}"
        echo -e "  JVM内存   : ${YELLOW}-Xms${JVM_XMS} -Xmx${JVM_XMX}${PLAIN}"
    else
        echo -e "  Node路径  : ${YELLOW}${NODE_HOME:-系统默认}${PLAIN}"
        echo -e "  包管理器  : ${YELLOW}${PKG_MANAGER}${PLAIN}"
        echo -e "  构建目录  : ${YELLOW}${DIST_DIR}${PLAIN}"
    fi
    print_title "────────────────────────────────────────"

    echo -ne "\n${CYAN}  保存配置？${PLAIN} [Y/n]: "
    local confirm
    read -r confirm
    if [[ "${confirm:-Y}" =~ ^[Yy]$ ]]; then
        save_config "$APP_PURE_NAME"
        echo ""
        print_info "✅ 初始化完成！后续可直接使用:"
        echo -e "  ${YELLOW}./deploy.sh ${APP_PURE_NAME} deploy${PLAIN}   # 部署"
        echo -e "  ${YELLOW}./deploy.sh ${APP_PURE_NAME} start${PLAIN}    # 启动"
        echo -e "  ${YELLOW}./deploy.sh ${APP_PURE_NAME} stop${PLAIN}     # 停止"
        echo -e "  ${YELLOW}./deploy.sh ${APP_PURE_NAME} status${PLAIN}   # 状态"
    else
        print_warn "已取消，配置未保存"
    fi
}

# ─── 部署核心逻辑 ─────────────────────────────────────────────

make_dirs() {
    make_dir_safe "$APP_HOME" || exit 1
    if [ "$PROJECT_TYPE" = "backend" ]; then
        make_dir_safe "$LOG_PATH" || exit 1
    fi
}

init_env() {
    make_dirs
    if [ "$PROJECT_TYPE" = "backend" ] && [ -n "$APP_JAVA_HOME" ] && [ -x "${APP_JAVA_HOME}/bin/java" ]; then
        export JAVA_HOME="$APP_JAVA_HOME"
        print_info "JAVA_HOME → $JAVA_HOME"
    fi
    if [ "$PROJECT_TYPE" = "frontend" ] && [ -n "$NODE_HOME" ] && [ -x "${NODE_HOME}/bin/node" ]; then
        export PATH="${NODE_HOME}/bin:$PATH"
        print_info "Node → $("${NODE_HOME}/bin/node" --version)"
    fi
}

pull_code() {
    git config --global credential.helper store
    if [ ! -d "$CODE_PATH" ]; then
        print_info "首次部署，克隆仓库..."
        make_dir_safe "$CODE_PATH_PARENT" || exit 1
        cd "$CODE_PATH_PARENT" || exit 1
        git clone "$GIT_REPO" || { print_error "git clone 失败"; exit 1; }
        cd "$CODE_PATH" || exit 1
    else
        print_info "拉取最新代码 (branch: ${GIT_BRANCH})..."
        cd "$CODE_PATH" || exit 1
        git pull origin "$GIT_BRANCH" || { print_error "git pull 失败"; exit 1; }
    fi
}

build_backend() {
    local jar_name=""

    if [ -f "./gradlew" ]; then
        print_info "检测到 Gradle Wrapper，开始编译..."
        [ ! -x ./gradlew ] && chmod +x ./gradlew
        ./gradlew clean build --no-daemon -x test || { print_error "Gradle 编译失败"; exit 1; }
        jar_name=$(ls build/libs/*.jar 2>/dev/null | grep -v plain | head -1)
    elif [ -f "./mvnw" ]; then
        print_info "检测到 Maven Wrapper，开始编译..."
        [ ! -x ./mvnw ] && chmod +x ./mvnw
        ./mvnw clean install -Dmaven.test.skip=true -P "${SPRING_PROFILE}" || { print_error "Maven 编译失败"; exit 1; }
        jar_name=$(ls target/"${APP_PURE_NAME}"*.jar 2>/dev/null | grep -v plain | head -1)
    elif [ -f "pom.xml" ]; then
        print_info "检测到 Maven 项目，开始编译..."
        mvn clean install -Dmaven.test.skip=true || { print_error "Maven 编译失败"; exit 1; }
        jar_name=$(ls target/"${APP_PURE_NAME}"*.jar 2>/dev/null | grep -v plain | head -1)
    elif [ -f "build.gradle" ] || [ -f "build.gradle.kts" ]; then
        print_info "检测到 Gradle 项目，开始编译..."
        gradle clean build --no-daemon || { print_error "Gradle 编译失败"; exit 1; }
        jar_name=$(ls build/libs/*.jar 2>/dev/null | grep -v plain | head -1)
    else
        print_error "无法识别的构建工具，请检查项目结构"
        exit 1
    fi

    if [ -z "$jar_name" ]; then
        print_error "未找到编译产物，请检查编译日志"
        exit 1
    fi

    print_info "编译产物: ${jar_name}"
    cp "$jar_name" "${APP_HOME}/${APP_PURE_NAME}.jar"
    print_info "已复制到: ${APP_HOME}/${APP_PURE_NAME}.jar"
}

build_frontend() {
    local pm="${PKG_MANAGER:-pnpm}"
    print_info "安装依赖 (${pm} install)..."
    ${pm} install || { print_error "依赖安装失败"; exit 1; }
    print_info "构建项目 (${pm} build)..."
    ${pm} build || { print_error "构建失败"; exit 1; }

    local dist="${DIST_DIR:-dist}"
    if [ ! -d "$dist" ]; then
        print_error "构建输出目录 [${dist}] 不存在，请检查构建配置"
        exit 1
    fi

    print_info "清空部署目录: ${APP_HOME}"
    rm -rf "${APP_HOME:?}"/*
    cp -R "${dist}/"* "${APP_HOME}/"
    print_info "前端文件已部署到: ${APP_HOME}"
}

check_process() {
    pid=$(pgrep -f "${APP_PURE_NAME}.jar" 2>/dev/null)
    [ -n "$pid" ]
}

start_app() {
    init_env
    if check_process; then
        print_warn "${APP_PURE_NAME}.jar 已在运行，PID: ${pid}"
        return
    fi

    cd "${APP_HOME}" || exit 1

    # ── 1. 自动 source 部署目录下的 .env（如果存在）────────────────
    if [ -f ".env" ]; then
        print_step "加载环境变量文件: ${APP_HOME}/.env"
        # shellcheck disable=SC1091
        set -a
        . ".env"
        set +a
    fi

    # ── 2. 构建 Java 命令 ──────────────────────────────────────────
    local java_bin="${APP_JAVA_HOME}/bin/java"
    [ ! -x "$java_bin" ] && java_bin="java"

    # JVM 内存参数
    local jvm_memory=""
    [ -n "${JVM_XMS}" ] && jvm_memory="-Xms${JVM_XMS}"
    [ -n "${JVM_XMX}" ] && jvm_memory="${jvm_memory} -Xmx${JVM_XMX}"

    # Spring profiles 支持多环境叠加（例: dev,local / prod,secret）
    local spring_profile_arg="-Dspring.profiles.active=${SPRING_PROFILE:-dev}"

    # 外部配置文件（放在 APP_HOME 下，不进 git）
    local config_extra_arg=""
    if [ -n "${APP_CONFIG_FILE}" ]; then
        # 允许 APP_CONFIG_FILE 用相对路径（相对于 APP_HOME）或绝对路径
        local abs_config="${APP_CONFIG_FILE}"
        if [[ "$abs_config" != /* ]]; then
            abs_config="${APP_HOME}/${abs_config}"
        fi
        if [ -f "${abs_config}" ]; then
            config_extra_arg="--spring.config.additional-location=file:${abs_config}"
            print_step "外部配置文件: ${abs_config}"
        else
            print_warn "外部配置文件不存在，跳过: ${abs_config}"
        fi
    fi

    # 完整命令
    local start_cmd="${java_bin} ${jvm_memory} ${JVM_EXTRA_OPTS} ${spring_profile_arg}"
    start_cmd="${start_cmd} ${config_extra_arg}"
    start_cmd="${start_cmd} -jar ${APP_PURE_NAME}.jar"

    print_step "启动命令:"
    print_step "  ${start_cmd} > ${LOG_FILE} 2>&1 &"

    nohup ${start_cmd} > "${LOG_FILE}" 2>&1 &

    print_info "等待日志输出..."
    sleep 1
    tail -20f "${LOG_FILE}" | sed '/seconds (process running for/Q'
    print_info "✅ ${APP_PURE_NAME} 启动成功!"
}

stop_app() {
    if check_process; then
        kill -9 "$pid"
        print_info "✅ ${APP_PURE_NAME} 已停止 (PID: ${pid})"
    else
        print_warn "${APP_PURE_NAME} 未在运行"
    fi
}

restart_app() {
    stop_app
    sleep 1
    start_app
}

status_app() {
    if check_process; then
        print_info "✅ ${APP_PURE_NAME} 运行中，PID: ${pid}"
    else
        print_warn "${APP_PURE_NAME} 未在运行"
    fi
}

delete_project() {
    local project="$1"
    local conf="${DEPLOY_CONFIG_DIR}/${project}.conf"
    if [ ! -f "$conf" ]; then
        print_error "项目 [${project}] 配置不存在"
        return 1
    fi
    echo -e "\n${RED}  ⚠ 即将删除项目 [${project}] 的部署配置${PLAIN}"
    echo -ne "  ${CYAN}确认删除？输入 yes 继续${PLAIN}: "
    local confirm
    read -r confirm
    if [ "$confirm" = "yes" ]; then
        rm -f "$conf"
        print_info "✅ 已删除项目 [${project}] 的配置"
    else
        print_warn "已取消删除"
    fi
}

deploy_app() {
    local start_ts start_fmt end_ts cost
    start_ts=$(date +%s)
    start_fmt=$(date '+%Y-%m-%d %H:%M:%S')

    echo ""
    print_title "═══════════════════════════════════════"
    print_title "  部署 [${APP_PURE_NAME}] - ${PROJECT_TYPE}"
    print_title "═══════════════════════════════════════"

    init_env
    pull_code

    if [ "$PROJECT_TYPE" = "backend" ]; then
        build_backend
        restart_app
        echo "deployed at $(date)" >> "${LOG_FILE}"
    else
        build_frontend
    fi

    end_ts=$(date +%s)
    cost=$((end_ts - start_ts))
    echo ""
    print_title "═══════════════════════════════════════"
    print_info "🎉 部署完成！开始: ${start_fmt}，耗时: ${cost}s"
    print_title "═══════════════════════════════════════"
}

run_action() {
    local action="$1"
    case "$action" in
        deploy)  deploy_app ;;
        start)
            [ "$PROJECT_TYPE" != "backend" ] && { print_error "前端项目无需 start"; exit 1; }
            start_app ;;
        stop)
            [ "$PROJECT_TYPE" != "backend" ] && { print_error "前端项目无需 stop"; exit 1; }
            stop_app ;;
        restart)
            [ "$PROJECT_TYPE" != "backend" ] && { print_error "前端项目无需 restart"; exit 1; }
            restart_app ;;
        status)
            [ "$PROJECT_TYPE" != "backend" ] && { print_error "前端项目无需 status"; exit 1; }
            status_app ;;
        *)
            print_error "未知动作: ${action}"
            echo "可用动作: deploy | start | stop | restart | status"
            exit 1 ;;
    esac
}

# ─── 交互菜单模式 ─────────────────────────────────────────────

cmd_menu() {
    while true; do
        clear
        # 注意：echo 带颜色变量必须用 echo -e，否则 \033[0m 会原样输出
        echo -e "${BOLD}${CYAN}  ╔═══════════════════════════════════════╗${PLAIN}"
        echo -e "${BOLD}${CYAN}  ║         项目部署管理                  ║${PLAIN}"
        echo -e "${BOLD}${CYAN}  ╚═══════════════════════════════════════╝${PLAIN}"

        local projects=()
        while IFS= read -r p; do
            [ -n "$p" ] && projects+=("$p")
        done < <(list_projects)

        echo -e "\n${BOLD}  已配置的项目:${PLAIN}"
        if [ ${#projects[@]} -eq 0 ]; then
            echo -e "  ${YELLOW}（暂无项目，请先初始化）${PLAIN}"
        else
            for i in "${!projects[@]}"; do
                local ptype=""
                ptype=$(grep '^PROJECT_TYPE=' "${DEPLOY_CONFIG_DIR}/${projects[$i]}.conf" 2>/dev/null | cut -d'"' -f2)
                local tag
                if [ "$ptype" = "frontend" ]; then
                    tag="${GREEN}[frontend]${PLAIN}"
                else
                    tag="${CYAN}[backend]${PLAIN}"
                fi
                echo -e "    ${YELLOW}$((i+1))${PLAIN}) ${projects[$i]}  ${tag}"
            done
        fi

        echo ""
        echo -e "  ${YELLOW}i${PLAIN}) 初始化新项目"
        echo -e "  ${YELLOW}d${PLAIN}) 删除项目"
        echo -e "  ${YELLOW}q${PLAIN}) 退出"
        echo ""
        echo -ne "${CYAN}  请选择${PLAIN}: "
        local choice
        read -r choice

        case "$choice" in
            q|Q) echo "再见！"; exit 0 ;;
            i|I) cmd_init; continue ;;
            d|D)
                if [ ${#projects[@]} -eq 0 ]; then
                    print_warn "暂无已配置的项目"
                    sleep 1
                    continue
                fi
                echo -e "\n${CYAN}  选择要删除的项目:${PLAIN}"
                for i in "${!projects[@]}"; do
                    echo -e "    ${YELLOW}$((i+1))${PLAIN}) ${projects[$i]}"
                done
                echo -ne "  ${CYAN}请输入编号${PLAIN}: "
                local del_choice
                read -r del_choice
                if [[ "$del_choice" =~ ^[0-9]+$ ]] && [ "$del_choice" -ge 1 ] && [ "$del_choice" -le ${#projects[@]} ]; then
                    delete_project "${projects[$((del_choice-1))]}"
                else
                    print_warn "无效选项"
                fi
                echo -ne "\n${CYAN}  按 Enter 返回菜单...${PLAIN}"
                read -r
                continue ;;
            *)
                if [[ "$choice" =~ ^[0-9]+$ ]] && [ "$choice" -ge 1 ] && [ "$choice" -le ${#projects[@]} ]; then
                    local proj="${projects[$((choice-1))]}"
                    load_config "$proj"

                    echo ""
                    echo -e "  ${BOLD}项目: ${proj} (${PROJECT_TYPE})${PLAIN}"
                    echo -e "  ${YELLOW}1${PLAIN}) 部署 (git pull + 构建 + 重启)"
                    if [ "$PROJECT_TYPE" = "backend" ]; then
                        echo -e "  ${YELLOW}2${PLAIN}) 启动"
                        echo -e "  ${YELLOW}3${PLAIN}) 停止"
                        echo -e "  ${YELLOW}4${PLAIN}) 重启"
                        echo -e "  ${YELLOW}5${PLAIN}) 查看状态"
                        echo -e "  ${YELLOW}6${PLAIN}) 删除项目"
                    else
                        echo -e "  ${YELLOW}2${PLAIN}) 删除项目"
                    fi
                    echo -e "  ${YELLOW}b${PLAIN}) 返回"
                    echo -ne "\n${CYAN}  请选择操作${PLAIN}: "
                    local action_choice
                    read -r action_choice
                    case "$action_choice" in
                        1) run_action deploy ;;
                        2)
                            if [ "$PROJECT_TYPE" = "backend" ]; then
                                run_action start
                            else
                                delete_project "$proj"
                            fi ;;
                        3) run_action stop ;;
                        4) run_action restart ;;
                        5) run_action status ;;
                        6) delete_project "$proj" ;;
                        b|B) continue ;;
                        *) print_warn "无效选项" ;;
                    esac
                    echo -ne "\n${CYAN}  按 Enter 返回菜单...${PLAIN}"
                    read -r
                else
                    print_warn "无效选项: ${choice}"
                    sleep 1
                fi ;;
        esac
    done
}

# ─── 入口 ─────────────────────────────────────────────────────

case "${1:-}" in
    "")
        cmd_menu
        ;;
    "init")
        cmd_init
        ;;
    "help"|"--help"|"-h")
        echo -e "${BOLD}用法:${PLAIN}"
        echo -e "  ${YELLOW}./deploy.sh${PLAIN}                        交互菜单"
        echo -e "  ${YELLOW}./deploy.sh init${PLAIN}                   初始化新项目配置"
        echo -e "  ${YELLOW}./deploy.sh <项目名> <动作>${PLAIN}         快捷命令"
        echo ""
        echo -e "${BOLD}动作:${PLAIN} deploy | start | stop | restart | status"
        echo ""
        echo -e "${BOLD}示例:${PLAIN}"
        echo -e "  ./deploy.sh teaching-backend deploy"
        echo -e "  ./deploy.sh teaching-frontend deploy"
        echo -e "  ./deploy.sh teaching-backend start"
        ;;
    *)
        load_config "$1"
        run_action "${2:-deploy}"
        ;;
esac

