import { Octokit } from 'octokit';
import dotenv from 'dotenv';
import fs from 'fs';
import yargs from 'yargs';
import { hideBin } from 'yargs/helpers';

dotenv.config();

const octokit = new Octokit({
    auth: process.env.GITHUB_TOKEN,
});

const ACCEPT_HEADER = 'application/vnd.github.v3.text-match+json';
const RATE_LIMIT_WAIT_TIME = 60000; // 1 minute

let rateLimitResetTime = null;

/**
 * Waits for the rate limit to reset if necessary.
 */
const waitForRateLimitReset = async () => {
    if (rateLimitResetTime && new Date() < rateLimitResetTime) {
        const waitTime = rateLimitResetTime - new Date();
        console.log(`Rate limit exceeded. Waiting for ${waitTime / 1000} seconds.`);
        await new Promise(resolve => setTimeout(resolve, waitTime));
    }

    try {
        const { data: { resources: { core } } } = await octokit.rest.rateLimit.get();
        if (core.remaining === 0) {
            rateLimitResetTime = new Date(core.reset * 1000);
            const waitTime = rateLimitResetTime - new Date();
            console.log(`Rate limit exceeded. Waiting for ${waitTime / 1000} seconds.`);
            await new Promise(resolve => setTimeout(resolve, waitTime));
        } else {
            rateLimitResetTime = null;
        }
    } catch (error) {
        console.error('Error fetching rate limit:', error);
        rateLimitResetTime = new Date(Date.now() + RATE_LIMIT_WAIT_TIME);
        const waitTime = rateLimitResetTime - new Date();
        console.log(`Waiting for ${waitTime / 1000} seconds before retrying.`);
        await new Promise(resolve => setTimeout(resolve, waitTime));
    }
};

/**
 * Escapes HTML characters in a string.
 *
 * @param {string} unsafe - The string to escape.
 * @returns {string} - The escaped string.
 */
const escapeHtml = (unsafe) => {
    const escapeMap = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;',
    };
    return unsafe.replace(/[&<>"']/g, (match) => escapeMap[match]);
};

/**
 * Searches for code in the GitHub repository.
 *
 * @param {string} cdnString - The CDN string to search for.
 * @param {number} page - The page number to fetch.
 * @returns {Promise<Object>} - The search results.
 */
const searchCode = async (cdnString, page) => {
    await waitForRateLimitReset();
    return octokit.rest.search.code({
        q: `${cdnString} org:companieshouse`,
        per_page: 100,
        page,
        headers: {
            accept: ACCEPT_HEADER
        }
    });
};

/**
 * Retrieves the content of a file from a GitHub repository.
 *
 * @param {string} owner - The owner of the repository.
 * @param {string} repo - The name of the repository.
 * @param {string} path - The path to the file.
 * @returns {Promise<string>} - The file content.
 */
const getContent = async (owner, repo, path) => {
    await waitForRateLimitReset();
    const response = await octokit.rest.repos.getContent({
        owner,
        repo,
        path,
        mediaType: {
            format: 'raw',
        },
    });
    return response.data;
};

/**
 * Processes each line of the file content to find matches based on the provided pattern.
 *
 * @param {string[]} lines - The lines of the file content.
 * @param {RegExp} pattern - The regex pattern to match against each line.
 * @param {string} repo - The repository name.
 * @param {string} path - The file path within the repository.
 * @returns {Object[]} - An array of identified assets with details.
 */
const processLines = (lines, pattern, repo, path) => {
    const assets = [];
    lines.forEach((line, index) => {
        const match = line.trim().match(pattern);
        if (match) {
            assets.push({
                repository: repo,
                filepath: path,
                linenumber: index + 1,
                line: escapeHtml(line.trim()),
                name: match[1]
            });
        }
    });
    return assets;
};

/**
 * Retrieves all search results for a given CDN string.
 *
 * @param {string} cdnString - The CDN string to search for.
 * @returns {Promise<Object[]>} - An array of search results.
 */
const getAllResults = async (cdnString) => {
    await waitForRateLimitReset();
    const firstResponse = await searchCode(cdnString, 1);
    const allResults = [...firstResponse.data.items];

    const totalCount = firstResponse.data.total_count;
    const totalPages = Math.ceil(totalCount / 100);
    console.log(`${totalCount} potential matches found with ${cdnString}`);

    const pagePromises = [];
    for (let page = 2; page <= totalPages; page++) {
        pagePromises.push(searchCode(cdnString, page));
    }

    const pageResponses = await Promise.all(pagePromises);
    pageResponses.forEach(response => {
        allResults.push(...response.data.items);
    });

    return allResults;
};

/**
 * Processes the records to identify assets based on the provided pattern.
 *
 * @param {Object[]} records - The records to process.
 * @param {RegExp} pattern - The regex pattern to match against each line.
 * @param {Set} fileChecklist - A set to keep track of processed files.
 * @returns {Promise<Object[]>} - An array of identified assets with details.
 */
const processRecords = async (records, pattern, fileChecklist) => {
    const identifiedAssets = [];

    const contentPromises = records.map(async (record) => {
        const { owner: { login: owner }, name: repo } = record.repository;
        const { path } = record;

        if (!fileChecklist.has(`${repo}/${path}`)) {
            const content = await getContent(owner, repo, path);
            const lines = content.split('\n');
            const assets = processLines(lines, pattern, repo, path);
            identifiedAssets.push(...assets);
            fileChecklist.add(`${repo}/${path}`);
        }
    });

    await Promise.all(contentPromises);
    return identifiedAssets;
};

/**
 * Retrieves files from asset folders by searching for specific CDN strings.
 *
 * @param {string[]} searchStrings - An array of CDN strings to search for.
 * @returns {Promise<Object[]>} - A promise that resolves to an array of identified assets with details.
 */
const getFilesFromAssetFolders = async (searchStrings) => {
    const pattern = /<script\s+[^>]*src=["'][^"']*\/([^"']+\.js)["\']/i;
    const fileChecklist = new Set();
    const allResultsPromises = searchStrings.map(cdnString => getAllResults(cdnString));
    const allResultsArrays = await Promise.all(allResultsPromises);

    const allResults = allResultsArrays.flat();
    return processRecords(allResults, pattern, fileChecklist);
};

/**
 * Converts an array of asset data into a markdown table format.
 *
 * @param {Object[]} data - The array of asset data to convert.
 * @returns {string} - The markdown table as a string.
 */
const convertToMarkdownTable = (data) => {
    const headers = ['Repository', 'File Path', 'Line Number', 'Line', 'Asset Name'];
    const headerRow = `| ${headers.join(' | ')} |`;
    const separatorRow = `| ${headers.map(() => '---').join(' | ')} |`;
    const dataRows = data.map(row => `| ${row.repository} | ${row.filepath} | ${row.linenumber} | ${row.line} | ${row.name} |`);

    return [headerRow, separatorRow, ...dataRows].join('\n');
};

const argv = yargs(hideBin(process.argv)).array('searchStrings').argv;

(async () => {
    const searchStrings = argv.searchStrings;
    if (!searchStrings || searchStrings.length === 0) {
        console.error('Please provide an array of CDN strings using the --searchStrings option.');
        process.exit(1);
    }

    const results = await getFilesFromAssetFolders(searchStrings);

    if (results.length > 0) {
        const markdownContent = convertToMarkdownTable(results);
        fs.writeFileSync('identified_assets.md', markdownContent, 'utf8');
        console.log('Markdown file has been saved.');
    }
})();